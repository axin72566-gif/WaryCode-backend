package com.warycode.agent.skill;

/** 技能：name/description 来自 frontmatter，body 是给模型的完整操作指引。 */
public record Skill(String name, String description, String body) {
}
