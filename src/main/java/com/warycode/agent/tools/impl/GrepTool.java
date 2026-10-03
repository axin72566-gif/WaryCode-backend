package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** grep：正则搜索文件内容，输出 路径:行号: 内容，限 100 条，二进制/编码异常文件静默跳过。 */
public final class GrepTool extends WorkspaceTool implements Tool {

    private static final int MAX_HITS = 100;
    private static final int MAX_DEPTH = 8;

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
        return objectSchema(List.of("pattern"),
                "pattern", "string", "正则表达式",
                "path", "string", "起始子目录或文件, 默认为根",
                "include", "string", "文件名 glob 过滤, 如 *.java",
                "max_results", "integer", "最大匹配条数, 默认 100");
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        Path base = resolve(args.containsKey("path") ? arg(args, "path") : ".");
        Pattern regex = Pattern.compile(arg(args, "pattern"));
        PathMatcher include = args.containsKey("include")
                ? FileSystems.getDefault().getPathMatcher("glob:" + arg(args, "include"))
                : null;
        int maxHits = Math.min(argInt(args, "max_results", MAX_HITS), MAX_HITS);

        StringBuilder sb = new StringBuilder();
        int[] hits = {0};
        try (var stream = Files.walk(base, MAX_DEPTH)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                if (hits[0] >= maxHits) {
                    break;
                }
                Path rel = workspace.relativize(file);
                if (ignored(rel) || (include != null && !include.matches(file.getFileName()))) {
                    continue;
                }
                scanFile(file, rel, regex, sb, hits, maxHits);
            }
        }
        if (hits[0] == 0) {
            return "无匹配";
        }
        if (hits[0] >= maxHits) {
            sb.append("…[达 ").append(maxHits).append(" 条上限]\n");
        }
        return sb.toString();
    }

    private void scanFile(Path file, Path rel, Pattern regex, StringBuilder sb, int[] hits, int maxHits) {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            var it = lines.iterator();
            int lineNo = 0;
            while (it.hasNext() && hits[0] < maxHits) {
                String line = it.next();
                lineNo++;
                if (regex.matcher(line).find()) {
                    sb.append(rel).append(':').append(lineNo).append(": ").append(line.trim()).append('\n');
                    hits[0]++;
                }
            }
        } catch (Exception ignored) {
            // 二进制或非 UTF-8 文件：跳过
        }
    }
}
