package com.warycode.agent;

import cn.hutool.core.util.StrUtil;
import com.warycode.agent.agent.CodingAgent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 命令行交互循环：/ 开头的行按命令分发，其余作为任务交给 agent。
 * LLM 调用失败只报错不退出，历史保留可重试。
 */
public final class Repl {

    private final CodingAgent agent;

    public Repl(CodingAgent agent) {
        this.agent = agent;
    }

    /** 主循环，输入流结束或 /exit 时退出。 */
    public void run() throws IOException {
        // 构造不预读 stdin(勿换 IoUtil.getBomReader: 其构造时做 BOM 探测会阻塞到首次输入, 提示符就打不出来)
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            // println 自带换行: 按行渲染的 IDE 控制台也能立即显示提示符
            System.out.print("\n" + Ansi.teal(">"));

            String line = inputReader.readLine();
            if (line == null) {
                return;
            }
            line = StrUtil.trim(line);
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

    /** 分发命令，返回 false 表示退出程序。 */
    private boolean handleCommand(String line) {
        String command = StrUtil.subBefore(line, ' ', false);
        switch (command) {
            case "/exit", "/quit" -> {
                return false;
            }
            case "/clear" -> {
                agent.clear();
                System.out.println(Ansi.dim("(已清空对话)"));
            }
            case "/help" -> System.out.println("/exit /quit 退出 | /clear 清空对话");
            default -> System.out.println("未知命令: " + command + "，/help 查看可用命令");
        }
        return true;
    }

    /** 执行任务并打印回答；失败只报错不退出。 */
    private void runTask(String input) {
        try {
            String answer = agent.runTurn(input);
            System.out.println();
            System.out.println(Ansi.dim("  ========================== 回答 =========================="));
            System.out.println();
            System.out.println(answer);
            System.out.println();
            System.out.println(Ansi.dim("  ========================== 回答 =========================="));
        } catch (Exception e) {
            System.out.println(Ansi.red("[LLM 调用失败] ") + StrUtil.blankToDefault(e.getMessage(), e.toString()));
        }
    }
}
