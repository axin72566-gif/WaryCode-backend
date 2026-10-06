package com.warycode.agent.hooks;

import java.util.regex.Pattern;

/** 一条 hook 配置；matcher 为 null 表示工具事件下匹配全部工具，onFailure=block 时异常按拦截处理。 */
public record Hook(Pattern matcher, String command, long timeoutSeconds, boolean blockOnFailure) {

    /** 工具名是否命中 matcher(全词正则语义)；matcher 为 null 全命中。 */
    public boolean matches(String toolName) {
        return matcher == null || matcher.matcher(toolName).matches();
    }
}
