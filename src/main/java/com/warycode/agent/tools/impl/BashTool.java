package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Bash：在工作目录执行 shell 命令。
 * 进程级超时(destroyForcibly 释放系统资源) + 虚拟线程异步读输出(防管道死锁)，
 * timeoutSeconds()=300 作为执行器层兜底。
 */
public final class BashTool extends WorkspaceTool implements Tool {

    public BashTool(Path workspace) {
        super(workspace);
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return "在工作目录执行 shell 命令(Windows 用 cmd /c, 其余 sh -c)，用于编译/测试/git 等操作";
    }

    @Override
    public int timeoutSeconds() {
        return 300;
    }

    @Override
    public Map<String, Object> schema() {
        return objectSchema(List.of("command"),
                "command", "string", "要执行的命令",
                "timeout", "integer", "超时秒数, 默认 60");
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String command = arg(args, "command");
        int timeout = Math.min(argInt(args, "timeout", 60), 280);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");

        Process process = new ProcessBuilder(
                        windows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command))
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();

        StringBuilder out = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader r = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // 进程被终止时流的关闭异常无需处理
            }
        });

        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "命令超时(" + timeout + "s)已终止\n" + out;
        }
        reader.join(5000);
        return "exit=" + process.exitValue() + "\n" + out;
    }
}
