package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 命令行交互循环（REPL）：读一行输入——
 * 以 / 开头交给 handleCommand 分发，否则当作任务交给 agent 执行。
 * LLM 调用失败只打断当前轮，历史保留可直接重试。
 */
public final class Repl {

    private final CodingAgent agent;

    public Repl(CodingAgent agent) {
        this.agent = agent;
    }

    /** 主循环：输入流结束（Ctrl+D / Ctrl+Z）或 /exit 命令时退出。 */
    public void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\n" + Ansi.teal("> "));
            System.out.flush();

            String line = in.readLine();
            if (line == null) {
                return;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("/")) {
                if (!handleCommand(line)) {
                    return;
                }
            } else {
                runTask(line);
            }
        }
    }

    /** 命令分发：一个 case 一个命令；返回 false 表示退出程序。 */
    private boolean handleCommand(String line) {
        switch (line) {
            case "/exit", "/quit" -> {
                return false;
            }
            case "/clear" -> {
                agent.clear();
                System.out.println(Ansi.dim("(已清空对话)"));
            }
            case "/help" -> System.out.println("/exit /quit 退出 | /clear 清空对话");
            default -> System.out.println("未知命令: " + line + "，/help 查看可用命令");
        }
        return true;
    }

    /** 执行一轮任务；回答前加空行和分隔横幅，与上方工具过程日志区分；失败只报错，不退出循环。 */
    private void runTask(String input) {
        try {
            String answer = agent.runTurn(input);
            System.out.println();
            System.out.println(Ansi.dim("  ── 回答 ──────────────"));
            System.out.println(answer);
        } catch (Exception e) {
            System.out.println(Ansi.red("[LLM 调用失败] ") + (e.getMessage() != null ? e.getMessage() : e));
        }
    }
}
