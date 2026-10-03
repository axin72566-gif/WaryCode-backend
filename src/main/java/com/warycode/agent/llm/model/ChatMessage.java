package com.warycode.agent.llm.model;

/**
 * 内部消息协议（与任何 LLM SDK 解耦）。
 * sealed 使适配层的转换 switch 成为 exhaustive：新增消息类型时编译器强制适配层同步。
 */
public sealed interface ChatMessage permits SystemMessage, UserMessage, AssistantMessage, ToolMessage {
}
