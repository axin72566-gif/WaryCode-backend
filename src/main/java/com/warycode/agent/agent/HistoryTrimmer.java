package com.warycode.agent.agent;

import com.warycode.agent.llm.model.ChatMessage;
import com.warycode.agent.llm.model.ToolMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文瘦身：history 只增不减且每次请求全量重发，字节大头是旧工具结果(文件内容/命令输出)。
 * 把最近 KEEP_RECENT 条之外的工具结果内容替换为占位符——只换内容不删消息，toolCallId 原样
 * 保留，OpenAI 的 tool_call 配对不受影响；模型仍能按上方 assistant 消息里的调用参数重新取回。
 */
public final class HistoryTrimmer {

    /** 完整保留的最近工具结果条数，与单条 16KB 截断一起构成上下文体积的结构性上限。 */
    private static final int KEEP_RECENT = 10;

    private static final String PLACEHOLDER_PREFIX = "[旧工具结果已省略";

    private HistoryTrimmer() {
    }

    /** 就地把过旧的工具结果内容替换为占位符；幂等，重复调用无副作用。 */
    public static void trim(List<ChatMessage> history) {
        List<Integer> toolIndexes = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i) instanceof ToolMessage) {
                toolIndexes.add(i);
            }
        }
        int evictCount = toolIndexes.size() - KEEP_RECENT;
        for (int k = 0; k < evictCount; k++) {
            int index = toolIndexes.get(k);
            ToolMessage message = (ToolMessage) history.get(index);
            // 已是占位符的跳过保幂等；比占位符还短的换过去反而更长
            if (message.content().startsWith(PLACEHOLDER_PREFIX)
                    || message.content().length() <= placeholder(0).length()) {
                continue;
            }
            history.set(index, new ToolMessage(message.toolCallId(),
                    placeholder(message.content().length()), message.isError()));
        }
    }

    private static String placeholder(int originalLength) {
        return "[旧工具结果已省略 | 原约 " + originalLength + " 字符 | 如仍需要请按上方调用参数重新执行]";
    }

    /** 内容以占位符开头即视为已省略，供 /history 观测统计。 */
    static boolean isElided(String content) {
        return content.startsWith(PLACEHOLDER_PREFIX);
    }
}
