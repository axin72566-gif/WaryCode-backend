package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.openai.OpenAiChatClient;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;

import java.io.IOException;
import java.nio.file.Path;

/** 入口：装配组件并启动 REPL。 */
public final class Main {

    public static void main(String[] args) throws IOException {
        AgentConfig config = AgentConfig.load();
        Path workspace = Path.of("").toAbsolutePath().normalize();
        ChatClient chatClient = new OpenAiChatClient(config.apiKey(), config.baseUrl());
        ToolRegistry toolRegistry = ToolRegistry.of(workspace);
        ToolExecutor executor = new ToolExecutor(toolRegistry);
        CodingAgent agent = new CodingAgent(chatClient, toolRegistry, executor, config, workspace);

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
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "workspace")), workspace);
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "tools")), String.join(", ", toolRegistry.names()));
        System.out.println();
        System.out.println(Ansi.dim("  输入任务开始; /help 查看命令"));

        new Repl(agent).run();
    }
}
