package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** grep：正则搜索文件内容，输出 路径:行号: 内容，限 100 条。 */
public final class GrepTool implements Tool {

    private static final int MAX_HITS = 100;
    private static final int MAX_DEPTH = 16;
    private static final Set<String> IGNORED_DIRS = Set.of(".git", "target", "node_modules", ".idea");

    private final Path workspace;

    public GrepTool(Path workspace) {
        this.workspace = workspace;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "正则搜索工作目录内文件内容，输出 路径:行号: 内容";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of("type", "string", "description", "正则表达式"),
                        "path", Map.of("type", "string", "description", "起始子目录或文件, 默认为根"),
                        "include", Map.of("type", "string", "description", "文件名 glob 过滤, 如 *.java"),
                        "max_results", Map.of("type", "integer", "description", "最大匹配条数, 默认 100")),
                "required", List.of("pattern"));
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        if (args.get("pattern") == null) throw new IllegalArgumentException("缺少必填参数: pattern");
        Object pathArg = args.get("path");
        String rawPath = pathArg == null ? "." : pathArg.toString();
        Path searchRoot = workspace.resolve(rawPath).normalize();
        if (!searchRoot.startsWith(workspace)) throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + rawPath);

        Pattern regex = Pattern.compile(args.get("pattern").toString());
        Object includeArg = args.get("include");
        PathMatcher includeFilter = includeArg == null ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + includeArg);
        Object maxArg = args.get("max_results");
        int maxHits = maxArg == null ? MAX_HITS : Math.min(Integer.parseInt(maxArg.toString().trim()), MAX_HITS);

        List<Path> files = new ArrayList<>();
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
                if (attrs.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        StringBuilder result = new StringBuilder();
        int hits = 0;
        for (Path file : files) {
            if (hits >= maxHits) break;
            Path relative = workspace.relativize(file);
            if (includeFilter != null && !includeFilter.matches(file.getFileName())) continue;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                int lineNo = 0;
                String line;
                while (hits < maxHits && (line = reader.readLine()) != null) {
                    lineNo++;
                    if (regex.matcher(line).find()) {
                        result.append(relative).append(':').append(lineNo).append(": ").append(line.trim()).append('\n');
                        hits++;
                    }
                }
            } catch (IOException ignored) {
                // 二进制/非 UTF-8 文件跳过
            }
        }

        if (hits == 0) return "无匹配";
        if (hits >= maxHits) result.append("…[达 ").append(maxHits).append(" 条上限]\n");
        return result.toString();
    }
}
