package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.openai.OpenAiChatClient;
import com.warycode.agent.skill.Skill;
import com.warycode.agent.skill.SkillLoader;
import com.warycode.agent.skill.SkillRegistry;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;
import com.warycode.agent.tools.impl.SkillTool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;

/** 入口：装配组件并启动 REPL。 */
public final class Main {

    public static void main(String[] args) throws IOException {
        AgentConfig config = AgentConfig.load();
        ChatClient chatClient = new OpenAiChatClient(config.apiKey(), config.baseUrl());
        SkillRegistry skillRegistry = SkillLoader.load(Path.of("skills"));
        ToolRegistry toolRegistry = ToolRegistry.of(config.workspace());
        if (!skillRegistry.isEmpty()) {
            toolRegistry.register(new SkillTool(skillRegistry));
        }
        ToolExecutor executor = new ToolExecutor(toolRegistry);
        CodingAgent agent = new CodingAgent(chatClient, toolRegistry, executor, config, skillRegistry);

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
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "tools")),
                String.join(", ", toolRegistry.names()));
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "skills")),
                skillRegistry.isEmpty() ? "无"
                        : skillRegistry.all().size() + " 个（" + skillRegistry.all().stream()
                        .map(Skill::name).collect(Collectors.joining(", ")) + "）");
        System.out.println();
        System.out.println(Ansi.dim("  输入任务开始; /help 查看命令"));

        new Repl(agent, skillRegistry).run();
    }
}
