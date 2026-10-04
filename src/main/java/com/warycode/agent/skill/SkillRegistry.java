package com.warycode.agent.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 技能注册表：启动时构建一次，之后只读。系统提示词只放清单，正文由模型经 skill 工具按需加载。 */
public final class SkillRegistry {

    private final Map<String, Skill> skills;

    SkillRegistry(Map<String, Skill> skills) {
        this.skills = new LinkedHashMap<>(skills);
    }

    public Optional<Skill> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }

    public Collection<Skill> all() {
        return skills.values();
    }
}
