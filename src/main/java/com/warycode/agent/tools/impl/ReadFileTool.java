package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** readFile：带行号读取文本文件，大文件用 offset/limit 分段。 */
public final class ReadFileTool extends WorkspaceTool implements Tool {

    private static final int MAX_CHARS = 64 * 1024;

    public ReadFileTool(Path workspace) {
        super(workspace);
    }

    @Override
    public String name() {
        return "readFile";
    }

    @Override
    public String description() {
        return "读取工作目录内的文本文件，返回带行号的内容；大文件用 offset/limit 分段读取";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of("type", "string", "description", "相对工作目录的文件路径"),
                        "offset", Map.of("type", "integer", "description", "起始行号(1 起,含)"),
                        "limit", Map.of("type", "integer", "description", "读取行数")),
                "required", List.of("path"));
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String rawPath = arg(args, "path");
        Path file = resolve(rawPath);
        if (Files.notExists(file)) {
            throw new IllegalArgumentException("文件不存在: " + rawPath);
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int fromLine = Math.max(1, argInt(args, "offset", 1));
        if (fromLine > lines.size()) {
            return "(文件共 " + lines.size() + " 行, offset " + fromLine + " 超出范围)";
        }
        int limit = argInt(args, "limit", lines.size());
        int toLine = Math.min(lines.size(), fromLine - 1 + limit);

        StringBuilder result = new StringBuilder("(共 " + lines.size() + " 行, 显示 " + fromLine + "-" + toLine + ")\n");
        for (int lineNo = fromLine; lineNo <= toLine; lineNo++) {
            result.append(String.format("%5d| %s%n", lineNo, lines.get(lineNo - 1)));
        }
        String content = result.toString();
        return content.length() > MAX_CHARS
                ? content.substring(0, MAX_CHARS) + "\n…[超64KB已截断,请用 offset/limit 分段读取]"
                : content;
    }
}
