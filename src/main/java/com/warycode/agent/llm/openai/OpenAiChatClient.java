package com.warycode.agent.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.warycode.agent.llm.ChatClient;
import com.warycode.agent.llm.model.AssistantMessage;
import com.warycode.agent.llm.model.ChatMessage;
import com.warycode.agent.llm.model.ChatRequest;
import com.warycode.agent.llm.model.ChatResponse;
import com.warycode.agent.llm.model.SystemMessage;
import com.warycode.agent.llm.model.ToolCall;
import com.warycode.agent.llm.model.ToolDefinition;
import com.warycode.agent.llm.model.ToolMessage;
import com.warycode.agent.llm.model.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI SDK 适配器：全工程唯一允许 import com.openai.* 的地方。
 * complete() 三步走：请求转 SDK 类型 → 调 API → 响应转内部模型，
 * SDK 变化的影响面收敛在本文件。
 */
public final class OpenAiChatClient implements ChatClient {

    private final OpenAIClient client;

    public OpenAiChatClient(String apiKey, String baseUrl) {
        this.client = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build();
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        // 第 1 步：请求转 SDK 类型。消息走 sealed switch（新增消息类型时编译器强制补分支），
        // 工具定义把 schema 的纯 Map 塞进 SDK 的 FunctionParameters
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.model());
        for (ChatMessage m : request.messages()) {
            builder.addMessage(switch (m) {
                case SystemMessage s -> ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder().content(s.content()).build());
                case UserMessage u -> ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder().content(u.content()).build());
                case AssistantMessage a -> {
                    ChatCompletionAssistantMessageParam.Builder b = ChatCompletionAssistantMessageParam.builder();
                    if (a.text() != null) {   // text 可为 null：纯工具调用轮模型通常不输出文本
                        b.content(a.text());
                    }
                    for (ToolCall c : a.toolCalls()) {
                        b.addToolCall(ChatCompletionMessageFunctionToolCall.builder()
                                .id(c.id())
                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                        .name(c.name())
                                        .arguments(c.argumentsJson())
                                        .build())
                                .build());
                    }
                    yield ChatCompletionMessageParam.ofAssistant(b.build());
                }
                case ToolMessage t -> ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(t.toolCallId())
                                // OpenAI 协议无错误标记字段，用前缀让模型明确感知失败
                                .content(t.isError() ? "[工具执行出错] " + t.content() : t.content())
                                .build());
            });
        }
        for (ToolDefinition def : request.tools()) {
            FunctionParameters.Builder params = FunctionParameters.builder();
            def.schema().forEach((key, value) -> params.putAdditionalProperty(key, JsonValue.from(value)));
            builder.addTool(ChatCompletionFunctionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name(def.name())
                            .description(def.description())
                            .parameters(params.build())
                            .build())
                    .build());
        }

        // 第 2 步：调 API
        ChatCompletionMessage message = client.chat().completions()
                .create(builder.build())
                .choices().getFirst()
                .message();

        // 第 3 步：响应转内部模型；argumentsJson 原样透传，解析推迟到工具边界
        List<ToolCall> toolCalls = new ArrayList<>();
        for (var tc : message.toolCalls().orElse(List.of())) {
            var call = tc.asFunction();   // 本工程只用 function 型 tool call
            var fn = call.function();
            toolCalls.add(new ToolCall(call.id(), fn.name(), fn.arguments()));
        }
        return new ChatResponse(message.content().orElse(null), toolCalls);
    }
}
