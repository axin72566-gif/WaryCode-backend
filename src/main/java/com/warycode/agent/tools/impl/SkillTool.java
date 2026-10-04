package com.warycode.agent.tools.impl;

import com.warycode.agent.skill.Skill;
import com.warycode.agent.skill.SkillRegistry;
import com.warycode.agent.tools.Tool;

import java.util.List;
import java.util.Map;

/**
 * skill 工具：按名取回技能的完整操作指引（SKILL.md 正文）。
 * 不继承 WorkspaceTool——查的是启动时构建的内存注册表，不走文件路径沙箱。
 * 注册前提：注册表非空（Main 装配处保证），模型经系统提示词中的清单决定何时调用。
 */
public final class SkillTool implements Tool {

    private final SkillRegistry skills;

    public SkillTool(SkillRegistry skills) {
        this.skills = skills;
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
        Object raw = args.get("name");
        String name = raw == null ? "" : String.valueOf(raw).trim();
        if (name.isEmpty()) {
            return "缺少参数 name。可用技能: " + names();
        }
        return skills.find(name)
                .map(Skill::body)
                .orElseGet(() -> "未知技能: " + name + "。可用技能: " + names());
    }

    private String names() {
        return skills.all().stream().map(Skill::name).toList().toString();
    }
}
