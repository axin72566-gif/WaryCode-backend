package com.warycode.agent;

import com.warycode.agent.llm.model.ToolCall;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;
import com.warycode.agent.tools.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工具框架冒烟测试（不依赖模型）：直接走 Registry → Executor 管道调用六个工具，
 * 并验证路径逃逸拒绝与未知工具报错。运行方式：
 * mvn -q test-compile exec:java -Dexec.mainClass=com.warycode.agent.ToolsSmokeTest -Dexec.classpathScope=test
 */
public final class ToolsSmokeTest {

    private static ToolExecutor executor;
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        Path ws = Path.of("target/smoke-workspace").toAbsolutePath().normalize();
        Files.createDirectories(ws);
        ToolRegistry registry = ToolRegistry.withDefaults(ws);
        executor = new ToolExecutor(registry);

        System.out.println("== workspace: " + ws);

        expectOk("writeFile", """
                {"path":"demo/Hello.java","content":"public class Hello {\\n  public static void main(String[] a){\\n    System.out.println(\\"hello\\");\\n  }\\n}\\n"}
                """);

        expectOk("readFile", "{\"path\":\"demo/Hello.java\"}");

        expectOk("editFile",
                "{\"path\":\"demo/Hello.java\",\"old_string\":\"hello\",\"new_string\":\"hi\"}");

        expectOk("readFile", "{\"path\":\"demo/Hello.java\",\"offset\":2,\"limit\":2}");

        expectOk("Bash", "{\"command\":\"echo smoke-ok\"}");

        expectOk("glob", "{\"pattern\":\"**/*.java\"}");

        expectOk("grep", "{\"pattern\":\"System\\\\.out\",\"include\":\"*.java\"}");

        // 路径逃逸：必须被沙箱拒绝
        expectError("readFile", "{\"path\":\"../../pom.xml\"}");

        // 未知工具：报错并列出可用工具
        expectError("deleteFile", "{\"path\":\"demo/Hello.java\"}");

        // editFile 匹配不唯一：应报错（文件里没有两个 hello 了，用 println 行重复验证）
        expectOk("writeFile", "{\"path\":\"dup.txt\",\"content\":\"aa\\nbb\\naa\\nbb\\n\"}");
        expectError("editFile", "{\"path\":\"dup.txt\",\"old_string\":\"bb\",\"new_string\":\"cc\"}");

        System.out.println("\n冒烟测试全部通过 (" + passed + " 项符合预期)");
    }

    private static void expectOk(String tool, String argsJson) {
        ToolResult r = executor.execute(new ToolCall("t", tool, argsJson));
        if (r.isError()) {
            throw new AssertionError("预期成功但失败: " + tool + " -> " + r.content());
        }
        System.out.println("  [OK] " + tool + " => " + abbreviate(r.content()));
        passed++;
    }

    private static void expectError(String tool, String argsJson) {
        ToolResult r = executor.execute(new ToolCall("t", tool, argsJson));
        if (!r.isError()) {
            throw new AssertionError("预期失败但成功: " + tool + " -> " + abbreviate(r.content()));
        }
        System.out.println("  [预期失败] " + tool + " => " + abbreviate(r.content()));
        passed++;
    }

    private static String abbreviate(String s) {
        String one = s.replace("\n", " ⏎ ");
        return one.length() > 160 ? one.substring(0, 160) + "…" : one;
    }
}
