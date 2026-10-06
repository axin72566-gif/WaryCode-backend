package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** editFile：精确字符串替换，old_string 须在文件中唯一出现。 */
public final class EditFileTool implements Tool {

    private final Path workspace;

    public EditFileTool(Path workspace) {
        this.workspace = workspace;
    }

    @Override
    public String name() {
        return "editFile";
    }

    @Override
    public String description() {
        return "精确替换文件内容：old_string 必须在文件中唯一出现，适合局部修改";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "相对工作目录的文件路径"),
                        "old_string", Map.of("type", "string", "description", "被替换的原文(须唯一)"),
                        "new_string", Map.of("type", "string", "description", "替换后的新文本")),
                "required", List.of("path", "old_string", "new_string"));
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String rawPath = args.get("path").toString();
        Path file = workspace.resolve(rawPath).normalize();
        if (!file.startsWith(workspace)) throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + rawPath);
        // hooks.json/config.properties 是 hook 与密钥配置: 禁 agent 写入防提示注入持久化，用户手动编辑不受限
        if (file.equals(workspace.resolve("hooks.json")) || file.equals(workspace.resolve("config.properties"))) {
            throw new IllegalArgumentException("受保护文件(hook/密钥配置)不允许 agent 修改，请让用户手动编辑: " + rawPath);
        }

        String originalContent = Files.readString(file, StandardCharsets.UTF_8);
        String oldString = args.get("old_string").toString();
        String newString = args.get("new_string").toString();
        int matchStart = originalContent.indexOf(oldString);
        if (matchStart < 0) {
            throw new IllegalArgumentException("old_string 未匹配(0 次)，请先 readFile 确认原文");
        }
        if (originalContent.indexOf(oldString, matchStart + 1) >= 0) {
            throw new IllegalArgumentException("old_string 匹配多次(须唯一)，请扩大上下文范围后重试");
        }
        Files.writeString(file, originalContent.substring(0, matchStart) + newString
                        + originalContent.substring(matchStart + oldString.length()),
                StandardCharsets.UTF_8);
        return "替换成功: " + workspace.relativize(file);
    }
}
