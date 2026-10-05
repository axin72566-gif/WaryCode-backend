package com.warycode.agent.agent;

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
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;
import com.warycode.agent.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;

/** 核心调度循环：调 LLM → 执行工具调用并回填结果 → 直到模型返回文本。工具异常由执行器兜底。 */
public final class CodingAgent {

    private static final int MAX_ITERATIONS = 24;

    private final ChatClient chatClient;
    private final ToolExecutor executor;
    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final String systemPrompt;
    private final List<ChatMessage> history = new ArrayList<>();

    public CodingAgent(ChatClient chatClient, ToolRegistry toolRegistry, ToolExecutor executor, AgentConfig config,
                       String systemPrompt) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.executor = executor;
        this.config = config;
        this.systemPrompt = systemPrompt;
    }

    /** 执行轮：调 LLM → 执行工具调用并回填结果 → 直到模型返回文本。工具异常由执行器兜底。 */
    public String runTurn(String userInput) {
        if (history.isEmpty()) {
            history.add(new SystemMessage(systemPrompt));
        }
        history.add(new UserMessage(userInput));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            ChatRequest request = new ChatRequest(
                    config.model(), List.copyOf(history), toolRegistry.definitions());
            ChatResponse response = chatClient.complete(request);

            history.add(new AssistantMessage(response.text(), response.toolCalls()));
            if (response.toolCalls().isEmpty()) {
                return response.text() == null ? "(模型未返回内容)" : response.text();
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
}
