package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** writeFile：整体覆盖写，父目录自动创建，适合新建与重写。 */
public final class WriteFileTool extends WorkspaceTool implements Tool {

    public WriteFileTool(Path workspace) {
        super(workspace);
    }

    @Override
    public String name() {
        return "writeFile";
    }

    @Override
    public String description() {
        return "写入文件(整体覆盖)，父目录不存在时自动创建";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "相对工作目录的文件路径"),
                        "content", Map.of("type", "string", "description", "完整文件内容")),
                "required", List.of("path", "content"));
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String raw = arg(args, "path");
        Path p = resolve(raw);
        String content = arg(args, "content");
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.writeString(p, content, StandardCharsets.UTF_8);
        long lineCount = content.lines().count();
        return "已写入 " + workspace.relativize(p) + " (" + content.length() + " 字符, " + lineCount + " 行)";
    }
}
