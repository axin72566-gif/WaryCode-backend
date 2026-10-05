package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Bash：在工作目录执行 shell 命令。输出由 OS 落盘到临时文件（无管道可堵），超时中断 waitFor。 */
public final class BashTool implements Tool {

    private final Path workspace;

    public BashTool(Path workspace) {
        this.workspace = workspace;
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return "在工作目录执行命令(直接写原始命令即可，不要再加 cmd /c 或 sh -c 前缀)，用于编译/测试/git 等操作";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "要执行的命令")),
                "required", List.of("command"));
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        if (args.get("command") == null) throw new IllegalArgumentException("缺少必填参数: command");
        String command = args.get("command").toString();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        Path outputFile = Files.createTempFile("warycode-bash-", ".out");

        Process process = new ProcessBuilder(
                        isWindows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command))
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        try {
            // 超时中断在此生效：cancel(true) 使 waitFor 抛 InterruptedException
            process.waitFor();
            return "exit=" + process.exitValue() + "\n" + Files.readString(outputFile, StandardCharsets.UTF_8);
        } finally {
            // Windows 上 destroyForcibly 只杀直接进程(cmd.exe)，需连进程树一起回收防孤儿
            try (var children = process.toHandle().descendants()) {
                children.forEach(ProcessHandle::destroyForcibly);
            }
            process.destroyForcibly();
            Files.delete(outputFile);
        }
    }
}
