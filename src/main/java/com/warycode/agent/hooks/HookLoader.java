package com.warycode.agent.hooks;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 启动时读取 hooks.json 装配各事件 hook 清单，与 skills 同哲学：项目根资产、改动需重启生效、损坏只告警跳过不中断启动。
 * 结构: {"hooks": {"PreToolUse": [{"matcher": "Bash", "command": "...", "timeout": 10, "onFailure": "block"}]}}。
 */
public final class HookLoader {

    private static final Path FILE = Path.of("hooks.json");
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    public Map<HookEvent, List<Hook>> load() {
        return load(FILE);
    }

    /** 按事件分组解析；文件缺失或整体损坏返回全空清单。 */
    Map<HookEvent, List<Hook>> load(Path file) {
        Map<HookEvent, List<Hook>> hooks = new EnumMap<>(HookEvent.class);
        for (HookEvent event : HookEvent.values()) {
            hooks.put(event, new ArrayList<>());
        }
        if (!Files.isRegularFile(file)) {
            return hooks;
        }
        JSONObject config;
        try {
            config = JSONUtil.parseObj(Files.readString(file, StandardCharsets.UTF_8)).getJSONObject("hooks");
        } catch (Exception e) {
            System.err.println("[hook] 跳过 " + file + ": 解析失败: " + e.getMessage());
            return hooks;
        }
        if (config == null) {
            return hooks;
        }
        for (String eventName : config.keySet()) {
            HookEvent event = parseEvent(eventName);
            JSONArray list = config.getJSONArray(eventName);
            if (event == null || list == null) {
                continue;
            }
            for (Object item : list) {
                loadOne(event, item, hooks);
            }
        }
        return hooks;
    }

    /** 解析单条 hook；缺 command 或 matcher 非法只告警跳过该条。 */
    private static void loadOne(HookEvent event, Object item, Map<HookEvent, List<Hook>> hooks) {
        if (!(item instanceof JSONObject config)) {
            System.err.println("[hook] 跳过一条 " + event + ": 条目须为对象");
            return;
        }
        String command = config.getStr("command");
        if (command == null || command.isBlank()) {
            System.err.println("[hook] 跳过一条 " + event + ": 缺 command");
            return;
        }
        Pattern matcher = null;
        String matcherText = config.getStr("matcher");
        if (matcherText != null && !matcherText.isBlank()) {
            try {
                matcher = Pattern.compile(matcherText.trim());
            } catch (PatternSyntaxException e) {
                System.err.println("[hook] 跳过一条 " + event + ": matcher 非法: " + e.getDescription());
                return;
            }
        }
        Long timeout = config.getLong("timeout");
        hooks.get(event).add(new Hook(matcher, command.trim(),
                timeout == null || timeout < 1 ? DEFAULT_TIMEOUT_SECONDS : timeout,
                "block".equalsIgnoreCase(config.getStr("onFailure"))));
    }

    private static HookEvent parseEvent(String name) {
        try {
            return HookEvent.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            System.err.println("[hook] 未知事件: " + name + "，可用: " + Arrays.toString(HookEvent.values()));
            return null;
        }
    }
}
