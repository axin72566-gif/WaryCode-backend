package com.warycode.agent.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
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
import com.warycode.agent.llm.model.ChatMessage;
import com.warycode.agent.llm.model.ChatRequest;
import com.warycode.agent.llm.model.ChatResponse;
import com.warycode.agent.llm.model.ToolCall;
import com.warycode.agent.llm.model.ToolDefinition;
import com.warycode.agent.llm.model.ToolMessage;

import java.util.List;

/**
 * OpenAI SDK 适配器：全工程唯一允许 import com.openai.* 的地方。
 * 负责内部模型与 SDK 类型的双向转换，SDK 变化的影响面收敛在本文件。
 */
public final class OpenAiChatClient implements ChatClient {

    private final OpenAIClient client;

    public OpenAiChatClient(String apiKey, String baseUrl) {
        this.client = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build();
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.model());
        request.messages().forEach(m -> builder.addMessage(toParam(m)));
        request.tools().forEach(t -> builder.addTool(toSdkTool(t)));

        ChatCompletion completion = client.chat().completions().create(builder.build());
        ChatCompletion.Choice choice = completion.choices().get(0);
        ChatCompletionMessage message = choice.message();

        List<ToolCall> toolCalls = message.toolCalls().orElse(List.of()).stream()
                .map(tc -> new ToolCall(
                        tc.asFunction().id(),
                        tc.asFunction().function().name(),
                        tc.asFunction().function().arguments()))
                .toList();

        return new ChatResponse(
                message.content().orElse(null),
                toolCalls,
                String.valueOf(choice.finishReason()));
    }

    private static ChatCompletionMessageParam toParam(ChatMessage msg) {
        return switch (msg) {
            case com.warycode.agent.llm.model.SystemMessage m ->
                    ChatCompletionMessageParam.ofSystem(
                            ChatCompletionSystemMessageParam.builder().content(m.content()).build());
            case com.warycode.agent.llm.model.UserMessage m ->
                    ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam.builder().content(m.content()).build());
            case com.warycode.agent.llm.model.AssistantMessage m -> {
                ChatCompletionAssistantMessageParam.Builder b = ChatCompletionAssistantMessageParam.builder();
                if (m.text() != null) {
                    b.content(m.text());
                }
                m.toolCalls().forEach(c -> b.addToolCall(toSdkToolCall(c)));
                yield ChatCompletionMessageParam.ofAssistant(b.build());
            }
            case ToolMessage m ->
                    ChatCompletionMessageParam.ofTool(
                            ChatCompletionToolMessageParam.builder()
                                    .toolCallId(m.toolCallId())
                                    // OpenAI 协议无错误标记字段，用前缀让模型明确感知失败
                                    .content(m.isError() ? "[工具执行出错] " + m.content() : m.content())
                                    .build());
        };
    }

    private static ChatCompletionMessageFunctionToolCall toSdkToolCall(ToolCall call) {
        return ChatCompletionMessageFunctionToolCall.builder()
                .id(call.id())
                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                        .name(call.name())
                        .arguments(call.argumentsJson())
                        .build())
                .build();
    }

    private static ChatCompletionFunctionTool toSdkTool(ToolDefinition def) {
        FunctionParameters.Builder params = FunctionParameters.builder();
        def.schema().forEach((key, value) -> params.putAdditionalProperty(key, JsonValue.from(value)));
        return ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(def.name())
                        .description(def.description())
                        .parameters(params.build())
                        .build())
                .build();
    }
}
