package com.warycode.agent.tools;

import cn.hutool.json.JSONUtil;
import com.warycode.agent.Ansi;
import com.warycode.agent.llm.model.ToolCall;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 工具执行入口：打日志 → 找工具 → 解析参数 → 线程池带超时执行 → 截断 → 打结果日志；异常与超时均转为错误结果回传模型。 */
public final class ToolExecutor {

    /** 执行预算(秒)，到点中断放弃；写死统一预算，不做按工具/按调用定制。 */
    private static final long TIMEOUT_SECONDS = 60;

    /** 防止撑爆对话上下文的单次结果上限。 */
    private static final int MAX_OUTPUT_CHARS = 16 * 1024;

    /** 工具执行线程池；工具串行执行，单线程即够；daemon 线程保证 main 返回后 JVM 正常退出。 */
    private final ExecutorService workerPool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tool-runner");
        thread.setDaemon(true);
        return thread;
    });

    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
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
        try {
            Map<String, Object> args = JSONUtil.parseObj(call.argumentsJson());
            Future<String> future = workerPool.submit(() -> tool.execute(args));
            try {
                output = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true); // 中断工作线程：waitFor 等阻塞操作立即响应，Files.* 在后台跑完但结果被丢弃
                throw new IllegalStateException("执行超时(" + TIMEOUT_SECONDS + "s)，已中断放弃；可缩小操作范围后重试");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof Exception ex ? ex : new RuntimeException(cause);
            }
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS) + "\n…[结果过长已截断]";
            }
        } catch (Exception e) {
            isError = true;
            output = e.getMessage() != null ? e.getMessage() : e.toString();
        }

        System.out.println(isError
                ? "  " + Ansi.red("✗ " + preview(output))
                : "  " + Ansi.dim("✓ " + preview(output)));
        return new ToolResult(isError, output);
    }

    /** 控制台日志用的单行预览，超 120 字符截断。 */
    private static String preview(String text) {
        String singleLine = text.replace("\n", " ");
        return singleLine.length() > 120 ? singleLine.substring(0, 120) + "…" : singleLine;
    }
}
