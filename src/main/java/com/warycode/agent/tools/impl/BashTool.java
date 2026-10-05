package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Bash：在工作目录执行 shell 命令。输出由 OS 落盘到临时文件（无管道可堵），超时中断 waitFor。 */
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
        return "在工作目录执行命令(直接写原始命令即可，不要再加 cmd /c 或 sh -c 前缀)，用于编译/测试/git 等操作";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "要执行的命令"),
                        "timeout", Map.of("type", "integer", "description", "超时秒数, 默认 60")),
                "required", List.of("command"));
    }

    /** 超时秒数取参数 timeout，默认 60，限制在 [1, 280]。 */
    @Override
    public long timeoutSeconds(Map<String, Object> args) {
        return Math.clamp(argInt(args, "timeout", 60), 1, 280);
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String command = arg(args, "command");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        Path outputFile = Files.createTempFile("warycode-bash-", ".out");
        outputFile.toFile().deleteOnExit();           // 兜底：句柄未释放删不掉时，退出时补删

        Process process = new ProcessBuilder(
                        isWindows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command))
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        try {
            process.waitFor();
            return "exit=" + process.exitValue() + "\n" + Files.readString(outputFile, StandardCharsets.UTF_8);
        } finally {
            // Windows 上 destroyForcibly 只杀直接进程(cmd.exe)，需连进程树一起回收防孤儿
            try (var children = process.toHandle().descendants()) {
                children.forEach(ProcessHandle::destroyForcibly);
            }
            process.destroyForcibly();
            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException e) {
                // 句柄未放完的偶发竞态，交给 deleteOnExit 兜底
            }
        }
    }
}
