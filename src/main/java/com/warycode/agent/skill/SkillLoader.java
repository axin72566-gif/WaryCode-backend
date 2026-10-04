package com.warycode.agent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 技能加载器：扫描技能根目录下的一级子目录，解析每个 SKILL.md 的
 * frontmatter（name/description）与正文，构建技能注册表。
 * 技能属于项目自身能力，目录相对启动目录解析（与 config.properties 同一约定），与 workspace 解耦。
 * 单个文件损坏只警告跳过，不让整个 agent 起不来。
 */
public final class SkillLoader {

    private SkillLoader() {
    }

    /** 扫描 skillsDir 下所有 skills/<name>/SKILL.md；目录不存在或为空返回空注册表。 */
    public static SkillRegistry load(Path skillsDir) {
        Map<String, Skill> skills = new LinkedHashMap<>();
        if (!Files.isDirectory(skillsDir)) {
            return new SkillRegistry(skills);
        }
        try (Stream<Path> dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory)
                    .map(Path::toAbsolutePath)
                    .sorted()
                    .forEach(dir -> loadOne(dir, skills));
        } catch (IOException e) {
            System.err.println("[skill] 扫描目录失败: " + e.getMessage());
        }
        return new SkillRegistry(skills);
    }

    /** 加载单个技能目录；无 SKILL.md 的目录静默忽略，其余问题警告后跳过。 */
    private static void loadOne(Path dir, Map<String, Skill> skills) {
        String dirName = dir.getFileName().toString();
        Path file = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Frontmatter fm = parse(Files.readString(file, StandardCharsets.UTF_8));
            if (fm == null || fm.name.isBlank() || fm.description.isBlank()) {
                System.err.println("[skill] 跳过 " + dirName + ": 缺少 frontmatter 或 name/description 字段");
                return;
            }
            if (!fm.name.equals(dirName)) {
                System.err.println("[skill] 警告: 目录名 " + dirName + " 与 frontmatter name \"" + fm.name + "\" 不一致，以 frontmatter 为准");
            }
            if (skills.containsKey(fm.name)) {
                throw new IllegalStateException("技能重名: " + fm.name);
            }
            skills.put(fm.name, new Skill(fm.name, fm.description, fm.body));
        } catch (IOException e) {
            System.err.println("[skill] 跳过 " + dirName + ": 读取失败 " + e.getMessage());
        }
    }

    /** 最小 frontmatter 解析：首行 --- 到下一个 --- 之间按第一个冒号切分键值，只认单行 name/description。 */
    private static Frontmatter parse(String content) {
        String[] lines = content.split("\n", -1);
        if (lines.length < 2 || !lines[0].trim().equals("---")) {
            return null;
        }
        String name = "";
        String description = "";
        int end = -1;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("---")) {
                end = i;
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "name" -> name = value;
                case "description" -> description = value;
                default -> { }
            }
        }
        if (end < 0) {
            return null; // 有开头无结尾的围栏，视为无 frontmatter
        }
        String body = String.join("\n", Arrays.copyOfRange(lines, end + 1, lines.length)).strip();
        return new Frontmatter(name, description, body);
    }

    private record Frontmatter(String name, String description, String body) {
    }
}
