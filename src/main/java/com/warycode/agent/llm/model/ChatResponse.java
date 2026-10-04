package com.warycode.agent.llm.model;

import java.util.List;

public record ChatResponse(String text, List<ToolCall> toolCalls) {
}
