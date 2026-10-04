package com.warycode.agent.llm.model;

import java.util.Map;

/** schema 为纯 Map 表达的 JSON schema 片段，不绑定 SDK 类型。 */
public record ToolDefinition(String name, String description, Map<String, Object> schema) {
}
