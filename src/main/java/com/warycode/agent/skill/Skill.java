package com.warycode.agent.skill;

/**
 * 技能数据模型：name/description 来自 frontmatter，body 是给模型的完整操作指引。
 * description 承担触发词职责（何时该用此技能），全量注入系统提示词是设计意图，不做截断。
 */
public record Skill(String name, String description, String body) {
}
