package com.warycode.agent.tools.impl;

import com.warycode.agent.tools.Tool;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Bash：在工作目录执行 shell 命令，进程管理全交 zt-exec：输出内存捕获、超时杀进程树、非零退出码不抛只回报。 */
public final class BashTool implements Tool {

    /** 命令执行预算(秒)，超时时 zt-exec 经 stopper 杀进程树并抛 TimeoutException，由执行器转错误结果。 */
    private static final long TIMEOUT_SECONDS = 60;

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
        String command = args.get("command").toString();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessResult result = new ProcessExecutor()
                .command(isWindows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command))
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .readOutput(true)
                .exitValueAny() // 非零退出码是正常结果(如编译失败)，连同 exit 值回传模型而非抛异常
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .stopper(BashTool::killProcessTree) // 超时/取消时连树回收
                .execute();
        return "exit=" + result.getExitValue() + "\n" + result.outputUTF8();
    }

    /** 先杀子孙再杀自身：Windows 上 destroy 只作用于直接进程(cmd.exe)，孙进程需连树回收防孤儿。 */
    private static void killProcessTree(Process process) {
        try (var children = process.toHandle().descendants()) {
            children.forEach(ProcessHandle::destroyForcibly);
        }
        process.destroyForcibly();
    }
}
