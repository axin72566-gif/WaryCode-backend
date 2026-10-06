package com.warycode.agent.tools;

import cn.hutool.json.JSONUtil;
import com.warycode.agent.Ansi;
import com.warycode.agent.hooks.HookEvent;
import com.warycode.agent.hooks.HookOutcome;
import com.warycode.agent.hooks.HookRunner;
import com.warycode.agent.llm.model.ToolCall;

import java.util.List;
import java.util.Map;

/** 工具执行入口：打日志 → 找工具 → 解析参数 → 校验必填 → PreToolUse hook → 执行 → 截断 → PostToolUse hook → 打结果日志；异常统一转错误结果回传模型。 */
public final class ToolExecutor {

    /** 防止撑爆对话上下文的单次结果上限。 */
    private static final int MAX_OUTPUT_CHARS = 16 * 1024;

    private final ToolRegistry registry;
    private final HookRunner hooks;

    public ToolExecutor(ToolRegistry registry, HookRunner hooks) {
        this.registry = registry;
        this.hooks = hooks;
    }

    public ToolResult execute(ToolCall call) {
        System.out.println("  " + Ansi.dim("→ ") + Ansi.teal(call.name()) + " " + Ansi.dim(preview(call.argumentsJson())));

        // 找不到工具不抛异常，把可用清单告诉模型让它重试
        Tool tool = registry.find(call.name());
        if (tool == null) {
            String message = "未知工具 " + call.name() + "，可用工具: " + registry.names();
            System.out.println("  " + Ansi.red("✗ " + message));
            return new ToolResult(true, message);
        }

        boolean isError = false;
        String output;
        Map<String, Object> args = Map.of();
        try {
            args = JSONUtil.parseObj(call.argumentsJson());
            String missing = missingRequired(tool, args);
            if (missing != null) throw new IllegalArgumentException("缺少必填参数: " + missing);

            HookOutcome pre = hooks.fire(HookEvent.PreToolUse, call.name(), args);
            if (pre.blocked()) {
                System.out.println("  " + Ansi.red("⊘ hook 拦截 ") + Ansi.dim(preview(pre.reason())));
                return new ToolResult(true, "[hook 拦截] " + pre.reason());
            }

            output = tool.execute(args);
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS) + "\n…[结果过长已截断]";
            }
        } catch (Exception e) {
            isError = true;
            // 带异常类型名，让 NoSuchFileException 这类只有路径信息的原生异常可读
            output = e.getMessage() == null ? e.toString() : e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        // PostToolUse 只对成功执行的工具质检；拦截意见追加到结果末尾供模型自纠
        if (!isError) {
            HookOutcome post = hooks.fire(HookEvent.PostToolUse, call.name(), args);
            if (post.blocked()) {
                output = output + "\n[hook] " + post.reason();
            }
        }

        System.out.println(isError
                ? "  " + Ansi.red("✗ " + preview(output))
                : "  " + Ansi.dim("✓ " + preview(output)));
        return new ToolResult(isError, output);
    }

    /** 按工具 schema 的 required 清单查参数齐全性，缺哪个返回哪个参数名，齐全返回 null。 */
    private static String missingRequired(Tool tool, Map<String, Object> args) {
        List<?> required = (List<?>) tool.schema().getOrDefault("required", List.of());
        for (Object key : required) {
            if (args.get(String.valueOf(key)) == null) return String.valueOf(key);
        }
        return null;
    }

    /** 控制台日志用的单行预览，超 120 字符截断。 */
    private static String preview(String text) {
        String singleLine = text.replace("\n", " ");
        return singleLine.length() > 120 ? singleLine.substring(0, 120) + "…" : singleLine;
    }
}
