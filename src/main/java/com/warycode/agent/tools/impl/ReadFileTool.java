package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** readFile：带行号读取文本文件，与 editFile 形成"看行号 → 精确改"的配合。 */
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
        String raw = arg(args, "path");
        Path p = resolve(raw);
        if (Files.notExists(p)) {
            throw new IllegalArgumentException("文件不存在: " + raw);
        }
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        int from = Math.max(1, argInt(args, "offset", 1));
        if (from > lines.size()) {
            return "(文件共 " + lines.size() + " 行, offset " + from + " 超出范围)";
        }
        int limit = argInt(args, "limit", lines.size());
        int to = Math.min(lines.size(), from - 1 + limit);

        StringBuilder sb = new StringBuilder("(共 " + lines.size() + " 行, 显示 " + from + "-" + to + ")\n");
        for (int i = from; i <= to; i++) {
            sb.append(String.format("%5d| %s%n", i, lines.get(i - 1)));
        }
        String content = sb.toString();
        return content.length() > MAX_CHARS
                ? content.substring(0, MAX_CHARS) + "\n…[超64KB已截断,请用 offset/limit 分段读取]"
                : content;
    }
}
