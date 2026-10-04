package com.warycode.agent.llm;

import com.warycode.agent.llm.model.ChatRequest;
import com.warycode.agent.llm.model.ChatResponse;

/** LLM 客户端接口：完成一次非流式请求。 */
public interface ChatClient {

    ChatResponse complete(ChatRequest request);
}
