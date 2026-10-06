package com.warycode.agent.hooks;

/** 一次事件的 hook 执行结论；blocked 时 reason 为回传模型/用户的文本(来自 hook 的 stderr)。 */
public record HookOutcome(boolean blocked, String reason) {

    public static HookOutcome allow() {
        return new HookOutcome(false, "");
    }

    public static HookOutcome block(String reason) {
        return new HookOutcome(true, reason);
    }
}
