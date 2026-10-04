package com.warycode.agent.llm.model;

import java.util.List;

/** text 可为 null（纯工具调用轮无文本）。 */
public record AssistantMessage(String text, List<ToolCall> toolCalls) implements ChatMessage {
}
