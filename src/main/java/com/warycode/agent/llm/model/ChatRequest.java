package com.warycode.agent.llm.model;

import java.util.List;

public record ChatRequest(String model, List<ChatMessage> messages, List<ToolDefinition> tools) {
}
