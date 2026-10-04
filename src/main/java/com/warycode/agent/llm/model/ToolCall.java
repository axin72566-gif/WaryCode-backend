package com.warycode.agent.llm.model;

/** argumentsJson 为模型返回的原始 JSON 串，解析推迟到工具边界。 */
public record ToolCall(String id, String name, String argumentsJson) {
}
