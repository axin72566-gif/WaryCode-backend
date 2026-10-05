package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** glob：按模式查找文件名，修改时间倒序，限 200 条。 */
public final class GlobTool extends WorkspaceTool implements Tool {

    private static final int MAX_RESULTS = 200;
    private static final int MAX_DEPTH = 16;

    public GlobTool(Path workspace) {
        super(workspace);
    }

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return "按 glob 模式查找文件名(如 src/**/*.java 或 **/*.md)，按修改时间倒序返回";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of("type", "string", "description", "glob 模式"),
                        "path", Map.of("type", "string", "description", "起始子目录, 默认为根")),
                "required", List.of("pattern"));
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        Path searchRoot = resolve(args.containsKey("path") ? arg(args, "path") : ".");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + arg(args, "pattern"));

        List<Path> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(searchRoot, MAX_DEPTH)) {
            for (Path file : stream.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Path relative = workspace.relativize(file);
                if (isIgnored(relative)) {
                    continue;
                }
                if (matcher.matches(relative)) {
                    matches.add(file);
                }
            }
        }

        matches.sort((a, b) -> Long.compare(lastModifiedTime(b), lastModifiedTime(a)));
        if (matches.size() > MAX_RESULTS) {
            matches = matches.subList(0, MAX_RESULTS);
        }

        if (matches.isEmpty()) {
            return "无匹配文件";
        }
        StringBuilder result = new StringBuilder("匹配 " + matches.size() + " 个:\n");
        for (Path file : matches) {
            result.append(workspace.relativize(file)).append('\n');
        }
        return result.toString();
    }

    private static long lastModifiedTime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}
