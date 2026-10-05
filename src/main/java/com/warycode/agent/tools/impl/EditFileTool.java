package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** editFile：精确字符串替换，old_string 须在文件中唯一出现。 */
public final class EditFileTool extends WorkspaceTool implements Tool {

    public EditFileTool(Path workspace) {
        super(workspace);
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
        String rawPath = arg(args, "path");
        Path file = resolve(rawPath);
        if (Files.notExists(file)) {
            throw new IllegalArgumentException("文件不存在: " + rawPath);
        }
        String originalContent = Files.readString(file, StandardCharsets.UTF_8);
        String oldString = arg(args, "old_string");
        String newString = arg(args, "new_string");

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
