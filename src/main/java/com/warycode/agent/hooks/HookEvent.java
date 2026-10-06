package com.warycode.agent.hooks;

/** hook 生命周期事件；matcher 过滤仅对两个工具事件生效。 */
public enum HookEvent {
    SessionStart,
    UserPromptSubmit,
    PreToolUse,
    PostToolUse,
    Stop
}
