package com.warycode.agent.llm;

import com.warycode.agent.llm.model.ChatRequest;
import com.warycode.agent.llm.model.ChatResponse;

/**
 * LLM 端口：全项目对"大模型"的全部抽象（非流式、一问一答）。
 * 换 SDK/供应商只需提供新地实现类，Agent 层与工具层不受影响。
 */
public interface ChatClient {

    ChatResponse complete(ChatRequest request);
}
