package com.warycode.agent.tools;

/** 工具执行结果：isError 是回传模型的自纠信号，错误不中断会话。 */
public record ToolResult(boolean isError, String content) {
}
