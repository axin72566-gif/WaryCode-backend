package com.warycode.agent.skills;

import java.nio.file.Path;

/** 技能：一份 SKILL.md 拆出的元数据 + 正文。 */
public record Skill(String name, String description, String content, Path path) {
}
