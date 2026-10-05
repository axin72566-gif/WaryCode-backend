package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** glob：按模式查找文件名，修改时间倒序，限 200 条。 */
public final class GlobTool implements Tool {

    private static final int MAX_RESULTS = 200;
    private static final int MAX_DEPTH = 16;

    private final Path workspace;

    public GlobTool(Path workspace) {
        this.workspace = workspace;
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
        Object pathArg = args.get("path");
        String rawPath = pathArg == null ? "." : pathArg.toString();
        Path searchRoot = workspace.resolve(rawPath).normalize();
        if (!searchRoot.startsWith(workspace)) throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + rawPath);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + args.get("pattern"));

        List<Path> matches;
        try (Stream<Path> files = Files.find(searchRoot, MAX_DEPTH,
                (file, attrs) -> attrs.isRegularFile() && matcher.matches(workspace.relativize(file)))) {
            matches = files.sorted(Comparator.comparingLong(GlobTool::modifiedAt).reversed())
                    .limit(MAX_RESULTS)
                    .toList();
        }

        if (matches.isEmpty()) {
            return "无匹配文件";
        }
        StringBuilder result = new StringBuilder("匹配 " + matches.size() + " 个:\n");
        for (Path match : matches) {
            result.append(workspace.relativize(match)).append('\n');
        }
        return result.toString();
    }

    private static long modifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
