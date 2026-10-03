package com.warycode.agent.llm.model;

/** isError 标记工具执行失败，供模型感知错误并自我纠正。 */
public record ToolMessage(String toolCallId, String content, boolean isError) implements ChatMessage {
}
