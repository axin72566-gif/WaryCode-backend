package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** writeFile：整体覆盖写文件，父目录自动创建。 */
public final class WriteFileTool implements Tool {

    private final Path workspace;

    public WriteFileTool(Path workspace) {
        this.workspace = workspace;
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
        String rawPath = args.get("path").toString();
        Path file = workspace.resolve(rawPath).normalize();
        if (!file.startsWith(workspace)) throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + rawPath);

        String content = args.get("content").toString();
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return "已写入 " + workspace.relativize(file) + " (" + content.length() + " 字符, " + content.lines().count() + " 行)";
    }
}
