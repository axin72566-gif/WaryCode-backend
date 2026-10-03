package com.warycode.agent.tools;

import java.util.Map;

/**
 * 工具契约：实现者只关心"做什么"（业务逻辑），
 * 超时/异常兜底/截断/日志等横切关注由 ToolExecutor 统一提供。
 */
public interface Tool {

    /** 唯一标识（如 "readFile"），注册时校验重名。 */
    String name();

    /** 给模型看的能力说明，描述质量直接影响模型调用准确率。 */
    String description();

    /** 参数的 JSON schema 片段：type=object + properties + required。 */
    Map<String, Object> schema();

    /** 执行工具并返回文本结果；异常是框架要处理的正常输入，不是事故。 */
    String execute(Map<String, Object> args) throws Exception;

    /** 执行器统一超时上限（秒），可按工具覆写。 */
    default int timeoutSeconds() {
        return 120;
    }
}
