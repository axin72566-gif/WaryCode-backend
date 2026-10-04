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

/**
 * 入口：只做组件装配和启动，交互循环在 Repl。
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        AgentConfig config = AgentConfig.load();
        ChatClient chatClient = new OpenAiChatClient(config.apiKey(), config.baseUrl());
        SkillRegistry skills = SkillLoader.load(Path.of("skills"));
        ToolRegistry registry = ToolRegistry.of(config.workspace());
        if (!skills.isEmpty()) {
            registry.register(new SkillTool(skills));
        }
        ToolExecutor executor = new ToolExecutor(registry);
        CodingAgent agent = new CodingAgent(chatClient, registry, executor, config, skills);

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
                String.join(", ", registry.names()));
        System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "skills")),
                skills.isEmpty() ? "无"
                        : skills.all().size() + " 个（" + skills.all().stream()
                        .map(Skill::name).collect(Collectors.joining(", ")) + "）");
        System.out.println();
        System.out.println(Ansi.dim("  输入任务开始; /help 查看命令"));

        new Repl(agent, skills).run();
    }
}
