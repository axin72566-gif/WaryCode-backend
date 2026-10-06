/// PreToolUse hook(Bash): 拦不可逆的 git 操作与对受保护文件的 shell 改写。
/// 协议: stdin 收事件 JSON; exit 0 放行, exit 2 拦截(stderr 原因会回传模型)。JDK 18+ 默认 UTF-8, 无需额外处理编码。
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitGuard {

    // --force 后不能跟 '-': 放行 --force-with-lease(安全变体); [^&|;]* 限定同一命令段, 防 "a && b" 跨段误判
    private static final Pattern FORCE_PUSH = Pattern.compile("git\\s+push\\b[^&|;]*(--force(?!-)|\\s-f(\\s|$))");
    private static final Pattern CLEAN = Pattern.compile("git\\s+clean\\b[^&|;]*\\s-[a-zA-Z]*f");
    private static final Pattern PROTECTED_FILE = Pattern.compile("(hooks\\.json|config\\.properties)");
    private static final Pattern WRITE_VERB = Pattern.compile(">>?|\\bdel\\b|\\brm\\b|\\bcopy\\b|\\bmove\\b|\\bmv\\b|\\bcp\\b|\\bren\\b|\\bsed\\s+-i|\\bSet-Content\\b|\\bOut-File\\b");

    public static void main(String[] args) throws IOException {
        String payload = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        String command = extract(payload, "command");
        if (command == null || command.isBlank()) {
            return; // 无从判定, 放行
        }
        if (FORCE_PUSH.matcher(command).find()) {
            block("禁止 force push: 先用 git pull --rebase 解决分歧再普通 push; 确需覆盖远程请用 --force-with-lease");
        }
        if (CLEAN.matcher(command).find()) {
            block("禁止 git clean -f: 会不可逆删除未跟踪文件(含新写的代码), 请逐个确认或让用户手动清理");
        }
        if (PROTECTED_FILE.matcher(command).find() && WRITE_VERB.matcher(command).find()) {
            block("hooks.json/config.properties 是受保护配置, 不允许经 shell 改写(防提示注入持久化); 读取请用 readFile 工具, 修改请让用户手动编辑");
        }
    }

    private static void block(String reason) {
        System.err.println(reason);
        System.exit(2);
    }

    /** 从 payload JSON 里抽取字符串字段; 只服务自家 HookRunner 的紧凑输出, 不做完整 JSON 解析。 */
    private static String extract(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"") : null;
    }
}
