package com.warycode.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warycode.agent.llm.model.ToolCall;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 执行器：所有工具的统一调用管道——
 * 流水日志 → 路由 → 参数解析 → 虚拟线程超时兜底 → 异常兜底 → 截断规范化。
 * 任何工具抛错都被转为错误 ToolResult，击不穿 agent 循环。
 */
public final class ToolExecutor {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESULT_CHARS = 16 * 1024;
    private static final int LOG_ABBREV = 120;

    private final ToolRegistry registry;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolResult execute(ToolCall call) {
        System.out.println("  → " + call.name() + " " + abbreviate(call.argumentsJson()));

        Tool tool = registry.byName(call.name()).orElse(null);
        if (tool == null) {
            return done(ToolResult.error(
                    "未知工具 " + call.name() + "，可用工具: " + registry.names()));
        }
        Future<String> future = null;
        try {
            Map<String, Object> args = JSON.readValue(call.argumentsJson(), new TypeReference<>() { });
            future = pool.submit(() -> tool.execute(args));
            String output = future.get(tool.timeoutSeconds(), TimeUnit.SECONDS);
            return done(ToolResult.ok(truncate(output)));
        } catch (TimeoutException e) {
            future.cancel(true);   // 中断虚拟线程，尽快释放
            return done(ToolResult.error("执行超时(" + tool.timeoutSeconds() + "s)已中断"));
        } catch (Exception e) {
            return done(ToolResult.error(rootMessage(e)));
        }
    }

    /** Future.get 会把工具异常包成 ExecutionException，解包取根因给模型更干净的错误信息。 */
    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause != cause.getCause()) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    private ToolResult done(ToolResult result) {
        System.out.println(result.isError()
                ? "  ✗ " + abbreviate(result.content())
                : "  ✓ " + abbreviate(result.content()));
        return result;
    }

    private static String truncate(String s) {
        return s.length() <= MAX_RESULT_CHARS ? s : s.substring(0, MAX_RESULT_CHARS) + "\n…[结果过长已截断]";
    }

    private static String abbreviate(String s) {
        String oneLine = s.replace("\n", " ");
        return oneLine.length() > LOG_ABBREV ? oneLine.substring(0, LOG_ABBREV) + "…" : oneLine;
    }
}
