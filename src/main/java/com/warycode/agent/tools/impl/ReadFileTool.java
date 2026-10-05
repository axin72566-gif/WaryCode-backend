package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** readFile：带行号读取文本文件，大文件用 offset/limit 分段；超长结果由执行器统一截断。 */
public final class ReadFileTool implements Tool {

    private final Path workspace;

    public ReadFileTool(Path workspace) {
        this.workspace = workspace;
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
        String rawPath = args.get("path").toString();
        Path file = workspace.resolve(rawPath).normalize();
        if (!file.startsWith(workspace)) throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + rawPath);

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Object offsetArg = args.get("offset");
        int fromLine = offsetArg == null ? 1 : Math.max(1, Integer.parseInt(offsetArg.toString().trim()));
        if (fromLine > lines.size()) {
            throw new IllegalArgumentException("offset " + fromLine + " 超出文件范围(共 " + lines.size() + " 行)");
        }
        Object limitArg = args.get("limit");
        int limit = limitArg == null ? lines.size() : Integer.parseInt(limitArg.toString().trim());
        int toLine = Math.min(lines.size(), fromLine - 1 + limit);

        StringBuilder result = new StringBuilder("(共 " + lines.size() + " 行, 显示 " + fromLine + "-" + toLine + ")\n");
        for (int lineNo = fromLine; lineNo <= toLine; lineNo++) {
            result.append(String.format("%5d| %s%n", lineNo, lines.get(lineNo - 1)));
        }
        return result.toString();
    }
}
