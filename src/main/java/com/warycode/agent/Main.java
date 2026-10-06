package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.openai.OpenAiChatClient;
import com.warycode.agent.skills.Skill;
import com.warycode.agent.skills.SkillLoader;
import com.warycode.agent.skills.SkillRegistry;
import com.warycode.agent.hooks.HookEvent;
import com.warycode.agent.hooks.HookLoader;
import com.warycode.agent.hooks.HookOutcome;
import com.warycode.agent.hooks.HookRunner;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;
import com.warycode.agent.tools.impl.SkillTool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** 入口：装配组件并启动 REPL。 */
public final class Main {

    public static void main(String[] args) throws IOException {
        AgentConfig config = AgentConfig.load();
        Path workspace = Path.of("").toAbsolutePath().normalize();

        ChatClient chatClient = new OpenAiChatClient(config.apiKey(), config.baseUrl());

        ToolRegistry toolRegistry = ToolRegistry.of(workspace);
        SkillRegistry skillRegistry = new SkillLoader().load();
        if (!skillRegistry.names().isEmpty()) {
            toolRegistry.register(new SkillTool(skillRegistry));
        }
        HookRunner hooks = new HookRunner(new HookLoader().load(), workspace);
        ToolExecutor executor = new ToolExecutor(toolRegistry, hooks);

        String systemPrompt = """
                你是运行在用户本机的编码 agent。工作目录: %s（只能访问其中文件）。
                工具使用准则:
                1) 修改文件前先 readFile 看内容;
                2) 局部修改优先用 editFile 而非整文件重写;
                3) 用 glob/grep 定位代码;
                4) 用 Bash 编译/运行验证改动;
                5) 大文件用 offset/limit 分段读取;
                6) 任务完成后简述你做了哪些改动。
                """.formatted(workspace);
        if (!skillRegistry.all().isEmpty()) {
            StringBuilder sb = new StringBuilder(systemPrompt)
                    .append("\n可用技能: 任务匹配下列描述时，先调用 skill 工具获取其完整指引，再按指引工作:\n");
            for (Skill skill : skillRegistry.all()) {
                sb.append("- ").append(skill.name()).append(": ").append(skill.description()).append('\n');
            }
            systemPrompt = sb.toString();
        }

        CodingAgent agent = new CodingAgent(chatClient, toolRegistry, executor, config, systemPrompt);

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
        if (!skillRegistry.names().isEmpty()) {
            System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "skills")), String.join(", ", skillRegistry.names()));
        }
        if (!hooks.isEmpty()) {
            System.out.printf("  %s %s%n", Ansi.dim(String.format("%-10s", "hooks")), hooks.summary());
        }
        System.out.println();
        System.out.println(Ansi.dim("  输入任务开始; /help 查看命令"));

        // 横幅后触发 SessionStart；exit 2 无阻断语义，只把意见亮出来
        HookOutcome start = hooks.fire(HookEvent.SessionStart, Map.of());
        if (start.blocked()) {
            System.out.println("  " + Ansi.red("[hook] SessionStart 被拦截(仅提示): ") + start.reason());
        }

        new Repl(agent, hooks).run();
    }
}
