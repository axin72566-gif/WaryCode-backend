package com.warycode.agent.agent;

import com.warycode.agent.Ansi;
import com.warycode.agent.AgentConfig;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.model.AssistantMessage;
import com.warycode.agent.llm.model.ChatMessage;
import com.warycode.agent.llm.model.ChatRequest;
import com.warycode.agent.llm.model.ChatResponse;
import com.warycode.agent.llm.model.SystemMessage;
import com.warycode.agent.llm.model.ToolCall;
import com.warycode.agent.llm.model.ToolMessage;
import com.warycode.agent.llm.model.UserMessage;
import com.warycode.agent.skill.SkillRegistry;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;
import com.warycode.agent.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 核心调度循环：调 LLM → 执行工具调用并回填结果 → 直到模型返回文本。工具异常由执行器兜底。 */
public final class CodingAgent {

    private static final int MAX_ITERATIONS = 24;

    private final ChatClient chatClient;
    private final ToolExecutor executor;
    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final SkillRegistry skillRegistry;
    private final List<ChatMessage> history = new ArrayList<>();

    public CodingAgent(ChatClient chatClient, ToolRegistry toolRegistry, ToolExecutor executor, AgentConfig config,
                       SkillRegistry skillRegistry) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.executor = executor;
        this.config = config;
        this.skillRegistry = skillRegistry;
    }

    /** 执行轮：调 LLM → 执行工具调用并回填结果 → 直到模型返回文本。工具异常由执行器兜底。 */
    public String runTurn(String userInput) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt()));
        }
        history.add(new UserMessage(userInput));

        // 执行分隔线只在本轮首次工具调用前打一次：直接回答的轮次不产生多余横幅
        boolean executionBannerPrinted = false;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            ChatRequest request = new ChatRequest(
                    config.model(), List.copyOf(history), toolRegistry.definitions());
            ChatResponse response = chatClient.complete(request);

            history.add(new AssistantMessage(response.text(), response.toolCalls()));
            if (response.toolCalls().isEmpty()) {
                return response.text() == null ? "(模型未返回内容)" : response.text();
            }
            if (!executionBannerPrinted) {
                System.out.println();
                System.out.println(Ansi.dim("  ========================== 执行 =========================="));
                executionBannerPrinted = true;
            }
            for (ToolCall call : response.toolCalls()) {
                ToolResult result = executor.execute(call);
                history.add(new ToolMessage(call.id(), result.content(), result.isError()));
            }
        }
        return "(已达最大迭代 " + MAX_ITERATIONS + " 次，可继续输入引导我完成)";
    }

    /** 清空对话历史，System prompt 下轮自动重建。 */
    public void clear() {
        history.clear();
    }

    private String systemPrompt() {
        String basePrompt = """
                你是运行在用户本机的编码 agent。工作目录: %s（只能访问其中文件）。
                工具使用准则:
                1) 修改文件前先 readFile 看内容;
                2) 局部修改优先用 editFile 而非整文件重写;
                3) 用 glob/grep 定位代码;
                4) 用 Bash 编译/运行验证改动;
                5) 大文件用 offset/limit 分段读取;
                6) 任务完成后简述你做了哪些改动。
                """.formatted(config.workspace());
        if (skillRegistry.isEmpty()) {
            return basePrompt;
        }
        String skillsSection = "可用技能（任务匹配下方描述时，先调用 skill 工具加载完整指引，再按指引执行）:\n"
                + skillRegistry.all().stream()
                .map(skill -> "- " + skill.name() + ": " + skill.description())
                .collect(Collectors.joining("\n"));
        return basePrompt + "\n\n" + skillsSection;
    }
}
