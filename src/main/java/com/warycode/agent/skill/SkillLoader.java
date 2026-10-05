package com.warycode.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** 扫描技能目录解析 SKILL.md 构建注册表；单个技能损坏只警告跳过，不阻断启动。 */
public final class SkillLoader {

    private SkillLoader() {
    }

    /** 加载 skillsDir 下所有 <name>/SKILL.md；目录不存在返回空注册表。 */
    public static SkillRegistry load(Path skillsDir) {
        Map<String, Skill> skills = new LinkedHashMap<>();
        if (!Files.isDirectory(skillsDir)) {
            return new SkillRegistry(skills);
        }
        try (Stream<Path> subDirs = Files.list(skillsDir)) {
            subDirs.filter(Files::isDirectory)
                    .map(Path::toAbsolutePath)
                    .sorted()
                    .forEach(skillDir -> loadOne(skillDir, skills));
        } catch (IOException e) {
            System.err.println("[skill] 扫描目录失败: " + e.getMessage());
        }
        return new SkillRegistry(skills);
    }

    /** 加载单个技能目录；无 SKILL.md 静默忽略，其余问题警告后跳过。 */
    private static void loadOne(Path skillDir, Map<String, Skill> skills) {
        String skillDirName = skillDir.getFileName().toString();
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return;
        }
        try {
            Frontmatter frontmatter = parse(Files.readString(skillFile, StandardCharsets.UTF_8));
            if (frontmatter == null || frontmatter.name.isBlank() || frontmatter.description.isBlank()) {
                System.err.println("[skill] 跳过 " + skillDirName + ": 缺少 frontmatter 或 name/description 字段");
                return;
            }
            if (!frontmatter.name.equals(skillDirName)) {
                System.err.println("[skill] 警告: 目录名 " + skillDirName + " 与 frontmatter name \"" + frontmatter.name + "\" 不一致，以 frontmatter 为准");
            }
            if (skills.containsKey(frontmatter.name)) {
                throw new IllegalStateException("技能重名: " + frontmatter.name);
            }
            skills.put(frontmatter.name, new Skill(frontmatter.name, frontmatter.description, frontmatter.body));
        } catch (IOException e) {
            System.err.println("[skill] 跳过 " + skillDirName + ": 读取失败 " + e.getMessage());
        }
    }

    /** 解析 --- 围栏的 frontmatter（只认单行 name/description）与正文。 */
    private static Frontmatter parse(String content) {
        String[] lines = content.split("\n", -1);
        if (lines.length < 2 || !lines[0].trim().equals("---")) {
            return null;
        }
        String name = "";
        String description = "";
        int closingFenceIndex = -1;
        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (line.equals("---")) {
                closingFenceIndex = lineIndex;
                break;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }
            String key = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();
            switch (key) {
                case "name" -> name = value;
                case "description" -> description = value;
                default -> { }
            }
        }
        if (closingFenceIndex < 0) {
            return null; // 围栏未闭合，视为无 frontmatter
        }
        String body = String.join("\n", Arrays.copyOfRange(lines, closingFenceIndex + 1, lines.length)).strip();
        return new Frontmatter(name, description, body);
    }

    private record Frontmatter(String name, String description, String body) {
    }
}
