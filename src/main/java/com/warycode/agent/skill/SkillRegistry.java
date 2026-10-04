package com.warycode.agent.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 技能注册表：启动时由 SkillLoader 构建一次，之后只读。
 * 命名对齐 ToolRegistry——一个管工具、一个管技能。
 * 渐进式披露——系统提示词只放 name+description 清单，正文由模型经 skill 工具按需加载。
 */
public final class SkillRegistry {

    private final Map<String, Skill> skills;

    SkillRegistry(Map<String, Skill> skills) {
        this.skills = new LinkedHashMap<>(skills);
    }

    /** 按名查找技能。 */
    public Optional<Skill> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }

    /** 全部技能（注册序稳定，用于清单展示与错误提示）。 */
    public Collection<Skill> all() {
        return skills.values();
    }
}
