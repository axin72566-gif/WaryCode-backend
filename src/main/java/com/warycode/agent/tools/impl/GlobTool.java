package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;

/** glob：按模式查找文件名，修改时间倒序（最新改动优先），限 200 条。 */
public final class GlobTool extends WorkspaceTool implements Tool {

    private static final int MAX_RESULTS = 200;
    private static final int MAX_DEPTH = 8;

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
        return objectSchema(List.of("pattern"),
                "pattern", "string", "glob 模式",
                "path", "string", "起始子目录, 默认为根");
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        Path base = resolve(args.containsKey("path") ? arg(args, "path") : ".");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + arg(args, "pattern"));

        List<String> matches;
        try (var stream = Files.walk(base, MAX_DEPTH)) {
            matches = stream.filter(Files::isRegularFile)
                    .map(f -> Map.entry(f, workspace.relativize(f)))
                    .filter(e -> !ignored(e.getValue()))
                    .filter(e -> matcher.matches(e.getValue()))
                    .sorted((a, b) -> Long.compare(mtime(b.getKey()), mtime(a.getKey())))
                    .limit(MAX_RESULTS)
                    .map(e -> e.getValue().toString())
                    .toList();
        }
        return matches.isEmpty()
                ? "无匹配文件"
                : "匹配 " + matches.size() + " 个:\n" + String.join("\n", matches);
    }

    private static long mtime(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}
