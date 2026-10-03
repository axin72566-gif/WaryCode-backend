package com.warycode.agent.llm.model;

import java.util.Map;

/** schema 用纯 Map 表达 JSON schema 片段（type/properties/required），不绑定任何 SDK 类型。 */
public record ToolDefinition(String name, String description, Map<String, Object> schema) {
}
