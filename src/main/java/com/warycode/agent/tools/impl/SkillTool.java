package com.warycode.agent.tools.impl;

import com.warycode.agent.skill.Skill;
import com.warycode.agent.skill.SkillRegistry;
import com.warycode.agent.tools.Tool;

import java.util.List;
import java.util.Map;

/** skill 工具：按名返回技能的完整指引。查内存注册表，不走文件沙箱。 */
public final class SkillTool implements Tool {

    private final SkillRegistry skillRegistry;

    public SkillTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String name() {
        return "skill";
    }

    @Override
    public String description() {
        return "加载技能的完整操作指引。当任务匹配系统提示词中可用技能清单的描述时，先调用本工具获取指引再执行任务";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "技能名, 见可用技能清单")),
                "required", List.of("name"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object rawName = args.get("name");
        String skillName = rawName == null ? "" : String.valueOf(rawName).trim();
        if (skillName.isEmpty()) {
            return "缺少参数 name。可用技能: " + formatAvailableSkills();
        }
        return skillRegistry.find(skillName)
                .map(Skill::body)
                .orElseGet(() -> "未知技能: " + skillName + "。可用技能: " + formatAvailableSkills());
    }

    private String formatAvailableSkills() {
        return skillRegistry.all().stream().map(Skill::name).toList().toString();
    }
}
