package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.openai.OpenAiChatClient;
import com.warycode.agent.tools.ToolExecutor;
import com.warycode.agent.tools.ToolRegistry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 入口：装配所有组件 + REPL 循环。
 * LLM 调用失败只打断当前轮，历史保留可直接重试。
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        AgentConfig config = AgentConfig.load();
        ChatClient chatClient = new OpenAiChatClient(config.apiKey(), config.baseUrl());
        ToolRegistry registry = ToolRegistry.withDefaults(config.workspace());
        ToolExecutor executor = new ToolExecutor(registry);
        CodingAgent agent = new CodingAgent(chatClient, registry, executor, config);

        System.out.println("WaryCode Agent | model=" + config.model() + " | workspace=" + config.workspace());
        System.out.println("输入任务开始; /help 查看命令");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\n> ");
            System.out.flush();
            String line = in.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            switch (line) {
                case "/exit", "/quit" -> {
                    return;
                }
                case "/clear" -> {
                    agent.clear();
                    System.out.println("(已清空对话)");
                    continue;
                }
                case "/help" -> {
                    System.out.println("/exit /quit 退出 | /clear 清空对话");
                    continue;
                }
                default -> {
                }
            }
            try {
                System.out.println(agent.run(line));
            } catch (Exception e) {
                System.out.println("[LLM 调用失败] " + (e.getMessage() != null ? e.getMessage() : e));
            }
        }
    }
}
