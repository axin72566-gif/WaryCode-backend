package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Bash：在工作目录执行 shell 命令。
 * 输出重定向到临时文件由 OS 落盘——没有管道可堵，也就不需要读线程；
 * 工具线程只阻塞在可中断的 waitFor 上，超时预算由 ToolExecutor 统一执法。
 * finally 收尾：杀进程树(防孤儿) + 删临时文件。
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

    /** 预算取模型传的 timeout 参数，默认 60s；夹在 [1, 280] 防止时钟被拨到 0 或天荒地老。 */
    @Override
    public long timeoutSeconds(Map<String, Object> args) {
        return Math.clamp(argInt(args, "timeout", 60), 1, 280);
    }

    @Override
    public String execute(Map<String, Object> args) throws Exception {
        String command = arg(args, "command");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path log = Files.createTempFile("warycode-bash-", ".out");
        log.toFile().deleteOnExit();           // 兜底：被中断路径上孙进程句柄释放有竞态，当场删不掉时退出时补删

        Process process = new ProcessBuilder(
                        windows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command))
                .directory(workspace.toFile())
                .redirectErrorStream(true)     // stderr 并入
                .redirectOutput(log.toFile())  // 输出由 OS 直接落盘，无管道可堵
                .start();
        try {
            process.waitFor();                 // 唯一的阻塞点，可被超时中断直达 finally
            return "exit=" + process.exitValue() + "\n" + Files.readString(log, StandardCharsets.UTF_8);
        } finally {
            // Windows 上 destroyForcibly 只杀直接进程(cmd.exe)，孙进程会变孤儿，连进程树一起回收
            try (var children = process.toHandle().descendants()) {
                children.forEach(ProcessHandle::destroyForcibly);
            }
            process.destroyForcibly();         // 正常退出时是 no-op；被中断放弃时防止子进程变孤儿
            try {
                Files.deleteIfExists(log);
            } catch (IOException e) {
                // 进程树刚被杀、句柄未放完的偶发竞态：交给 deleteOnExit 兜底
            }
        }
    }
}
