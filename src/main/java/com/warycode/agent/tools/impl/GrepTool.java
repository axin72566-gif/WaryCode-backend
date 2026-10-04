package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** grep：正则搜索文件内容，输出 路径:行号: 内容，限 100 条。 */
public final class GrepTool extends WorkspaceTool implements Tool {

    private static final int MAX_HITS = 100;
    private static final int MAX_DEPTH = 16;

    public GrepTool(Path workspace) {
        super(workspace);
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
        Path base = resolve(args.containsKey("path") ? arg(args, "path") : ".");
        Pattern regex = Pattern.compile(arg(args, "pattern"));
        PathMatcher include = args.containsKey("include")
                ? FileSystems.getDefault().getPathMatcher("glob:" + arg(args, "include"))
                : null;
        int maxHits = Math.min(argInt(args, "max_results", MAX_HITS), MAX_HITS);

        List<Path> files;
        try (Stream<Path> stream = Files.walk(base, MAX_DEPTH)) {
            files = stream.toList();
        }

        StringBuilder sb = new StringBuilder();
        int hits = 0;
        for (Path file : files) {
            if (hits >= maxHits) {
                break;
            }
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Path relative = workspace.relativize(file);
            if (isIgnored(relative)) {
                continue;
            }
            if (include != null && !include.matches(file.getFileName())) {
                continue;
            }
            hits = scanFile(file, relative, regex, sb, hits, maxHits);
        }

        if (hits == 0) {
            return "无匹配";
        }
        if (hits >= maxHits) {
            sb.append("…[达 ").append(maxHits).append(" 条上限]\n");
        }
        return sb.toString();
    }

    /** 扫描单文件，命中行写入 sb，返回累计命中数；二进制/非 UTF-8 文件跳过。 */
    private int scanFile(Path file, Path relative, Pattern regex, StringBuilder sb, int hits, int maxHits) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int lineNo = 0;
            String line;
            while ((line = reader.readLine()) != null && hits < maxHits) {
                lineNo++;
                if (regex.matcher(line).find()) {
                    sb.append(relative).append(':').append(lineNo).append(": ").append(line.trim()).append('\n');
                    hits++;
                }
            }
        } catch (Exception ignored) {
        }
        return hits;
    }
}
