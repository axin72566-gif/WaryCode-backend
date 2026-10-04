package com.warycode.agent.tools;

import java.util.Map;

/** 工具接口；异常兜底、超时、截断、日志由 ToolExecutor 统一处理。 */
public interface Tool {

    String name();

    /** 给模型看的能力说明，描述质量直接影响调用准确率。 */
    String description();

    /** 参数 JSON schema（type/properties/required）。 */
    Map<String, Object> schema();

    /** 执行工具，返回文本结果；抛异常会被转为错误结果回传模型。 */
    String execute(Map<String, Object> args) throws Exception;

    /** 执行预算(秒)，默认 60，到点由 ToolExecutor 中断。中断是协作式：waitFor 立即响应，Files.* 等在后台跑完但结果被丢弃。 */
    default long timeoutSeconds(Map<String, Object> args) {
        return 60;
    }
}
