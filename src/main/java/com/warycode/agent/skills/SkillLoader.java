package com.warycode.agent.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** 启动时扫描 skills/ 下的 SKILL.md，解析 frontmatter 装配为 Skill。技能与 config.properties 同为项目自带资产，按启动目录相对路径读取。 */
public final class SkillLoader {

    private static final Path SKILLS_DIR = Path.of("skills");
    private static final String SKILL_FILE = "SKILL.md";
    private static final int MAX_DEPTH = 3;

    /** 扫描并装载全部技能；skills 目录不存在返回空注册表。单个文件损坏只告警跳过，不中断启动。 */
    public SkillRegistry load() throws IOException {
        SkillRegistry registry = new SkillRegistry();
        Path skillsDir = SKILLS_DIR;
        if (!Files.isDirectory(skillsDir)) {
            return registry;
        }
        try (Stream<Path> files = Files.find(skillsDir, MAX_DEPTH,
                (file, attrs) -> attrs.isRegularFile() && file.getFileName().toString().equalsIgnoreCase(SKILL_FILE))) {
            for (Path file : files.sorted().toList()) {
                loadOne(file, registry);
            }
        }
        return registry;
    }

    private void loadOne(Path file, SkillRegistry registry) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn(file, "读取失败: " + e.getMessage());
            return;
        }
        Frontmatter fm = Frontmatter.parse(text);
        String name = fm.value("name");
        if (name == null || name.isBlank()) {
            name = file.getParent().getFileName().toString();
        }
        String description = fm.value("description");
        if (description == null) {
            description = "";
        }
        try {
            registry.register(new Skill(name, description, fm.body(), file));
        } catch (IllegalStateException e) {
            warn(file, e.getMessage());
        }
    }

    private static void warn(Path file, String reason) {
        System.err.println("[skill] 跳过 " + file + ": " + reason);
    }

    /** 极简 frontmatter 解析：只认 --- 包裹块内的 key: value 行，不追求完整 YAML。 */
    private record Frontmatter(Map<String, String> values, String body) {

        static Frontmatter parse(String text) {
            String[] lines = text.split("\r?\n", -1);
            if (lines.length == 0 || !lines[0].trim().equals("---")) {
                return new Frontmatter(Map.of(), text);
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.equals("---")) {
                    String body = String.join("\n", Arrays.asList(lines).subList(i + 1, lines.length)).stripLeading();
                    return new Frontmatter(values, body);
                }
                int sep = line.indexOf(':');
                if (sep > 0) {
                    values.put(line.substring(0, sep).trim(), unquote(line.substring(sep + 1).trim()));
                }
            }
            // 只有开头的 --- 没有闭合，按无 frontmatter 处理
            return new Frontmatter(Map.of(), text);
        }

        private static String unquote(String value) {
            if (value.length() >= 2
                    && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        String value(String key) {
            return values.get(key);
        }
    }
}
