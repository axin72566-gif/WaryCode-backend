package com.warycode.agent.tools.impl;

import com.warycode.agent.skills.Skill;
import com.warycode.agent.skills.SkillRegistry;
import com.warycode.agent.tools.Tool;

import java.util.List;
import java.util.Map;

/** skill：按名称从注册中心取技能完整指引；不带 name 时列出可用技能名录。 */
public final class SkillTool implements Tool {

    private final SkillRegistry registry;

    public SkillTool(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return "获取技能指引: 传 name 返回该技能的完整正文, 供按其流程工作; 不传 name 列出全部可用技能的名录与描述";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "技能名, 如 project-briefing")),
                "required", List.of());
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object nameArg = args.get("name");
        if (nameArg == null || nameArg.toString().isBlank()) {
            return list();
        }
        String name = nameArg.toString().trim();
        Skill skill = registry.find(name);
        if (skill == null) {
            return "未找到技能: " + name + "; 可用: " + String.join(", ", registry.names());
        }
        return skill.content();
    }

    private String list() {
        if (registry.names().isEmpty()) {
            return "无可用技能";
        }
        StringBuilder sb = new StringBuilder("可用技能 " + registry.names().size() + " 个:\n");
        for (Skill skill : registry.all()) {
            sb.append("- ").append(skill.name()).append(": ").append(skill.description()).append('\n');
        }
        return sb.toString();
    }
}
