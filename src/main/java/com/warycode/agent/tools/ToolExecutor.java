package com.warycode.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warycode.agent.Ansi;
import com.warycode.agent.llm.model.ToolCall;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 工具执行入口：打日志 → 找工具 → 带超时执行 → 打结果日志；异常与超时均转为错误结果回传模型。 */
public final class ToolExecutor {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** 类型令牌，绕过泛型擦除供 readValue 还原 Map 类型。 */
    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<>() {
    };

    /** 工具执行线程池；daemon 线程保证 main 返回后 JVM 正常退出。 */
    private final ExecutorService runner = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-runner");
        t.setDaemon(true);
        return t;
    });

    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolResult execute(ToolCall call) {
        String argsPreview = call.argumentsJson().replace("\n", " ");
        if (argsPreview.length() > 120) {
            argsPreview = argsPreview.substring(0, 120) + "…";
        }
        System.out.println("  " + Ansi.dim("→ ") + Ansi.teal(call.name()) + " " + Ansi.dim(argsPreview));

        // 找不到工具不抛异常，把可用清单告诉模型让它重试
        Tool tool = registry.find(call.name());
        if (tool == null) {
            String msg = "未知工具 " + call.name() + "，可用工具: " + registry.names();
            System.out.println("  " + Ansi.red("✗ " + msg));
            return new ToolResult(true, msg);
        }

        boolean isError = false;
        String output;
        try {
            Map<String, Object> args = JSON.readValue(call.argumentsJson(), ARGS_TYPE);
            // Execute in a separate thread to support interruption on timeout; ExecutionException restored to the original exception
            long budget = tool.timeoutSeconds(args);
            Future<String> future = runner.submit(() -> tool.execute(args));
            try {
                output = future.get(budget, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IllegalStateException("执行超时(" + budget + "s)，已中断放弃；可缩小操作范围后重试");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof Exception checked) {
                    throw checked;
                }
                throw new RuntimeException(cause);
            }
            // 防止撑爆对话上下文，截断到 16KB
            if (output.length() > 16 * 1024) {
                output = output.substring(0, 16 * 1024) + "\n…[结果过长已截断]";
            }
        } catch (Exception e) {
            isError = true;
            output = (e.getMessage() != null) ? e.getMessage() : e.toString();
        }

        String resultPreview = output.replace("\n", " ");
        if (resultPreview.length() > 120) {
            resultPreview = resultPreview.substring(0, 120) + "…";
        }
        if (isError) {
            System.out.println("  " + Ansi.red("✗ " + resultPreview));
        } else {
            System.out.println("  " + Ansi.dim("✓ " + resultPreview));
        }
        return new ToolResult(isError, output);
    }
}
