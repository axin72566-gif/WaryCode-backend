/// PostToolUse hook(writeFile|editFile): Java 源码被改后立即 mvn test-compile,
/// 编译失败则 exit 2 把错误尾部回传, 模型当轮就能看到并修复, 不等任务收尾才发现编不过。
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CompileCheck {

    /** 回传错误尾部上限, 避免撑爆对话上下文。 */
    private static final int MAX_TAIL_CHARS = 3000;

    public static void main(String[] args) throws IOException, InterruptedException {
        String payload = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        String path = extract(payload, "path");
        if (path == null || !path.endsWith(".java")) {
            return; // 非 Java 文件不检查
        }
        String normalized = path.replace('\\', '/');
        if (!normalized.startsWith("src/main/java") && !normalized.startsWith("src/test/java")) {
            return; // 不在编译范围内(如 scripts/hooks/ 下的单文件 hook 脚本)
        }
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> mvn = windows ? List.of("cmd.exe", "/c", "mvn", "-q", "test-compile")
                : List.of("mvn", "-q", "test-compile");
        Process process = new ProcessBuilder(mvn).redirectErrorStream(true).start();
        // mvn/javac 在 Windows 控制台按系统编码(中文系统为 GBK)输出诊断, 按 UTF-8 解会乱码
        Charset consoleCharset = Charset.forName(System.getProperty("native.encoding", "UTF-8"));
        String output = new String(process.getInputStream().readAllBytes(), consoleCharset);
        int exit = process.waitFor();
        if (exit != 0) {
            String tail = output.strip();
            if (tail.length() > MAX_TAIL_CHARS) {
                tail = "…" + tail.substring(tail.length() - MAX_TAIL_CHARS);
            }
            System.err.println("编译未通过(mvn test-compile), 请根据错误继续修复:\n" + tail);
            System.exit(2);
        }
    }

    /** 从 payload JSON 里抽取字符串字段; 只服务自家 HookRunner 的紧凑输出, 不做完整 JSON 解析。 */
    private static String extract(String json, String field) {
        var m = java.util.regex.Pattern
                .compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"") : null;
    }
}
