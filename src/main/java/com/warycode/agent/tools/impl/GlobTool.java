package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** glob：按模式查找文件名，修改时间倒序，限 200 条。 */
public final class GlobTool implements Tool {

    private static final int MAX_RESULTS = 200;
    private static final int MAX_DEPTH = 16;
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "target", "node_modules", ".idea");

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
        if (args.get("pattern") == null) throw new IllegalArgumentException("缺少必填参数: pattern");
        Object pathArg = args.get("path");
        String rawPath = pathArg == null ? "." : pathArg.toString();
        Path searchRoot = workspace.resolve(rawPath).normalize();
        if (!searchRoot.startsWith(workspace)) throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + rawPath);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + args.get("pattern"));

        record Match(Path file, long modifiedAt) {}
        List<Match> matches = new ArrayList<>();
        Files.walkFileTree(searchRoot, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
                // 进入前剪枝：忽略目录整棵子树不遍历
                return IGNORED_DIRS.contains(dir.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                if (Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE; // 协作响应超时中断
                if (attrs.isRegularFile() && matcher.matches(workspace.relativize(file))) {
                    matches.add(new Match(file, attrs.lastModifiedTime().toMillis()));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        matches.sort(Comparator.comparingLong(Match::modifiedAt).reversed());
        List<Match> top = matches.size() > MAX_RESULTS ? matches.subList(0, MAX_RESULTS) : matches;
        if (top.isEmpty()) {
            return "无匹配文件";
        }
        StringBuilder result = new StringBuilder("匹配 " + top.size() + " 个:\n");
        for (Match match : top) {
            result.append(workspace.relativize(match.file())).append('\n');
        }
        return result.toString();
    }
}
