package com.warycode.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warycode.agent.llm.model.ToolCall;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 执行器：CodingAgent 调工具的唯一入口。
 * execute() 从上到下就是完整流程：打调用日志 → 按名找工具 → 带预算执行并兜底 → 打结果日志。
 * 超时和异常一样是"要转成错误结果的正常输入"：预算由工具声明(timeoutSeconds)，时钟和执法在这里。
 */
public final class ToolExecutor {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Jackson 的类型令牌：泛型运行时会被擦除，要用这个匿名子类把 Map<String,Object> 类型传给 readValue。 */
    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<>() {
    };

    /** 工具执行线程池：缓存的平台线程，空闲 60s 自动回收；daemon 属性保证 main 返回后 JVM 正常退出。 */
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
        // 第 1 步：打印调用日志。参数 JSON 压成一行、最多显示 120 字符
        String argsPreview = call.argumentsJson().replace("\n", " ");
        if (argsPreview.length() > 120) {
            argsPreview = argsPreview.substring(0, 120) + "…";
        }
        System.out.println("  → " + call.name() + " " + argsPreview);

        // 第 2 步：按名字从注册表找工具。找不到不抛异常，
        // 而是把可用工具清单告诉模型，让它换个名字重试
        Tool tool = registry.find(call.name());
        if (tool == null) {
            String msg = "未知工具 " + call.name() + "，可用工具: " + registry.names();
            System.out.println("  ✗ " + msg);
            return new ToolResult(true, msg);
        }

        // 第 3 步：解析参数 JSON，带预算执行工具；超时和异常都接住，转成错误结果
        boolean isError = false;
        String output;
        try {
            Map<String, Object> args = JSON.readValue(call.argumentsJson(), ARGS_TYPE);
            // 独立线程执行 + 预算时钟：到点中断放弃；工具线程抛出的异常从
            // ExecutionException 还原成原始异常——两类失败都落进下面的统一兜底
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
            // 结果太长会撑爆下一轮对话的上下文，截断到 16KB
            if (output.length() > 16 * 1024) {
                output = output.substring(0, 16 * 1024) + "\n…[结果过长已截断]";
            }
        } catch (Exception e) {
            isError = true;
            output = (e.getMessage() != null) ? e.getMessage() : e.toString();
        }

        // 第 4 步：打印结果日志，同样压成一行、最多 120 字符
        String resultPreview = output.replace("\n", " ");
        if (resultPreview.length() > 120) {
            resultPreview = resultPreview.substring(0, 120) + "…";
        }
        if (isError) {
            System.out.println("  ✗ " + resultPreview);
        } else {
            System.out.println("  ✓ " + resultPreview);
        }
        return new ToolResult(isError, output);
    }
}
