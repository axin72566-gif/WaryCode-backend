package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.openai.OpenAiChatClient;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;

import java.io.IOException;

/**
 * 入口：只做组件装配和启动，交互循环在 Repl。
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        AgentConfig config = AgentConfig.load();
        ChatClient chatClient = new OpenAiChatClient(config.apiKey(), config.baseUrl());
        ToolRegistry registry = ToolRegistry.of(config.workspace());
        ToolExecutor executor = new ToolExecutor(registry);
        CodingAgent agent = new CodingAgent(chatClient, registry, executor, config);

        System.out.print(Ansi.gradient("""
                ██╗    ██╗ █████╗ ██████╗ ██╗   ██╗ ██████╗ ██████╗ ██╗   ██╗ █████╗
                ██║    ██║██╔══██╗██╔══██╗██║   ██║██╔════╝██╔═══██╗██║   ██║██╔══██╗
                ██║ █╗ ██║███████║██████╔╝███████║ █████╗  ██║   ██║██║   ██║██████╔╝
                ██║███╗██║██╔══██║██╔══██╗╚════██║ ██╔══╝  ██║   ██║██║   ██║██╔══██╗
                ╚███╔███╔╝██║  ██║██║  ██║     ██║ ███████╗╚██████╔╝╚██████╔╝██║  ██║
                 ╚══╝╚══╝ ╚═╝  ╚═╝╚═╝  ╚═╝     ╚═╝ ╚══════╝ ╚═════╝  ╚═════╝ ╚═╝  ╚═╝
                """));
        System.out.println();
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "model")), config.model());
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "workspace")), config.workspace());
        System.out.println();
        System.out.println(Ansi.dim("  输入任务开始; /help 查看命令"));

        new Repl(agent).run();
    }
}
