package com.warycode.agent.llm.model;

/** argumentsJson 保留模型返回的原始 JSON 串（各供应商协议的最大公约数），解析推迟到工具边界。 */
public record ToolCall(String id, String name, String argumentsJson) {
}
