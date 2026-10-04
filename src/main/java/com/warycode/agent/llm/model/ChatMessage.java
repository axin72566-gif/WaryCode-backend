package com.warycode.agent.llm.model;

/** 内部消息协议，与 LLM SDK 解耦；sealed 使适配层 switch 穷尽。 */
public sealed interface ChatMessage permits SystemMessage, UserMessage, AssistantMessage, ToolMessage {
}
