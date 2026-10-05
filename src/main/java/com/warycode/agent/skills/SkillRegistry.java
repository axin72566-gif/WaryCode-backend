package com.warycode.agent.skills;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 技能注册中心：管注册、查找与卸载。 */
public final class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    /**
     * 注册技能，重名抛错。
     */
    public void register(Skill skill) {
        if (skills.containsKey(skill.name())) {
            throw new IllegalStateException("技能重名: " + skill.name());
        }
        skills.put(skill.name(), skill);
    }

    /** 按名查找，无则 null。 */
    public Skill find(String name) {
        return skills.get(name);
    }

    public List<Skill> all() {
        return List.copyOf(skills.values());
    }

    public List<String> names() {
        return List.copyOf(skills.keySet());
    }

    public int size() {
        return skills.size();
    }
}
