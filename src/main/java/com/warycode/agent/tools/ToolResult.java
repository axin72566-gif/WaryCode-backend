package com.warycode.agent.tools;

/** 工具执行结果；isError 标记失败，供模型自纠。 */
public record ToolResult(boolean isError, String content) {
}
