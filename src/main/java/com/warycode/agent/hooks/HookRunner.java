package com.warycode.agent.hooks;

import cn.hutool.json.JSONUtil;
import com.warycode.agent.Ansi;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * hook 执行器：构造 payload → 按配置顺序串行起子进程(JSON 写 stdin) → 退出码翻译为放行/拦截。
 * 语义: exit 0 放行(stdout 打 dim 日志)、exit 2 拦截(stderr 为回传模型的原因)、
 * 其他退出码/超时/崩溃默认 fail-open 放行并告警，配 onFailure=block 时改为拦截。
 * 数据只走 stdin 不做命令行模板插值，规避 shell 注入。
 */
public final class HookRunner {

    private final Map<HookEvent, List<Hook>> hooks;
    private final Path workspace;
    private final String sessionId = UUID.randomUUID().toString();

    public HookRunner(Map<HookEvent, List<Hook>> hooks, Path workspace) {
        this.hooks = hooks;
        this.workspace = workspace;
    }

    /** 是否一个 hook 都没配；未配置时 fire 零开销。 */
    public boolean isEmpty() {
        return hooks.values().stream().allMatch(List::isEmpty);
    }

    /** 横幅一行摘要，如 "PreToolUse×2 Stop×1"。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<HookEvent, List<Hook>> entry : hooks.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(entry.getKey()).append('×').append(entry.getValue().size());
        }
        return sb.toString();
    }

    /** 触发非工具事件(SessionStart/UserPromptSubmit/Stop)；extra 并入 payload(如 prompt 字段)。 */
    public HookOutcome fire(HookEvent event, Map<String, Object> extra) {
        return fire(event, null, null, extra);
    }

    /** 触发工具事件：按 matcher 过滤后执行，payload 携带 tool_name/tool_input。 */
    public HookOutcome fire(HookEvent event, String toolName, Map<String, Object> toolInput) {
        return fire(event, toolName, toolInput, Map.of());
    }

    private HookOutcome fire(HookEvent event, String toolName, Map<String, Object> toolInput,
                             Map<String, Object> extra) {
        List<Hook> matching = hooks.getOrDefault(event, List.of()).stream()
                .filter(hook -> toolName == null || hook.matches(toolName))
                .toList();
        if (matching.isEmpty()) {
            return HookOutcome.allow();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hook_event_name", event.name());
        payload.put("session_id", sessionId);
        payload.put("workspace", workspace.toString());
        if (toolName != null) {
            payload.put("tool_name", toolName);
            payload.put("tool_input", toolInput == null ? Map.of() : toolInput);
        }
        payload.putAll(extra);
        String json = JSONUtil.toJsonStr(payload);
        for (Hook hook : matching) {
            HookOutcome outcome = runOne(hook, json);
            if (outcome.blocked()) {
                return outcome; // 首个拦截即短路
            }
        }
        return HookOutcome.allow();
    }

    /** 起子进程执行单条 hook 并解释退出码；stderr 由本方缓冲捕获(zt-exec 只内置 stdout 缓冲)。 */
    private HookOutcome runOne(Hook hook, String payloadJson) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ProcessResult result;
        try {
            result = new ProcessExecutor()
                    .command(shell(hook.command()))
                    .directory(workspace.toFile())
                    .environment("WARYCODE_WORKSPACE", workspace.toString())
                    .redirectInput(new ByteArrayInputStream(payloadJson.getBytes(StandardCharsets.UTF_8)))
                    .readOutput(true)
                    .redirectError(stderr)
                    .exitValueAny()
                    .timeout(hook.timeoutSeconds(), TimeUnit.SECONDS)
                    .stopper(HookRunner::killProcessTree)
                    .execute();
        } catch (TimeoutException e) {
            return hookFailed(hook, "超时(" + hook.timeoutSeconds() + "s)已杀进程树");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return hookFailed(hook, "被中断");
        } catch (Exception e) {
            return hookFailed(hook, e.getMessage() == null ? e.toString() : e.getMessage());
        }
        String error = stderr.toString(StandardCharsets.UTF_8);
        if (result.getExitValue() == 0) {
            String stdout = result.outputUTF8();
            if (stdout != null && !stdout.isBlank()) {
                System.out.println("  " + Ansi.dim("[hook] " + singleLine(stdout)));
            }
            return HookOutcome.allow();
        }
        if (result.getExitValue() == 2) {
            return HookOutcome.block(reasonOf(error));
        }
        String detail = error.isBlank() ? "" : " " + singleLine(error);
        return hookFailed(hook, "exit=" + result.getExitValue() + detail);
    }

    /** hook 崩溃/超时/非约定退出码：默认 fail-open 放行并告警，配置 onFailure=block 则拦截。 */
    private static HookOutcome hookFailed(Hook hook, String cause) {
        System.err.println("[hook] " + hook.command() + " " + cause);
        return hook.blockOnFailure()
                ? HookOutcome.block("hook 异常(" + cause + ")，按 onFailure=block 拦截")
                : HookOutcome.allow();
    }

    /** exit 2 的拦截原因取自 stderr；未输出则给默认话术。 */
    private static String reasonOf(String stderr) {
        String reason = stderr == null ? "" : stderr.strip();
        return reason.isEmpty() ? "被 hook 拦截(未说明原因)" : reason;
    }

    private static List<String> shell(String command) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        return isWindows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command);
    }

    /** 同 BashTool: Windows destroy 只作用于直接进程，需连树回收防孤儿。 */
    private static void killProcessTree(Process process) {
        try (var children = process.toHandle().descendants()) {
            children.forEach(ProcessHandle::destroyForcibly);
        }
        process.destroyForcibly();
    }

    /** 控制台日志用单行预览，对齐 ToolExecutor 的 120 字符上限。 */
    private static String singleLine(String text) {
        String s = text.strip().replace("\n", " ");
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
