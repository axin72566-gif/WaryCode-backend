package com.warycode.agent.llm.model;

public record SystemMessage(String content) implements ChatMessage {
}
