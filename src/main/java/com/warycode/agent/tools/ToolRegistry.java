package com.warycode.agent.tools;

import com.warycode.agent.llm.model.ToolDefinition;
import com.warycode.agent.tools.impl.BashTool;
import com.warycode.agent.tools.impl.EditFileTool;
import com.warycode.agent.tools.impl.GlobTool;
import com.warycode.agent.tools.impl.GrepTool;
import com.warycode.agent.tools.impl.ReadFileTool;
import com.warycode.agent.tools.impl.WriteFileTool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册中心：只管工具的生命周期与发现，不做任何执行。
 * definitions() 是工具框架与 LLM 层的唯一接缝——注册即生效，新工具自动进入下次请求。
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /** 注册工具，重名 fail-fast，链式调用。 */
    public ToolRegistry register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalStateException("工具重名: " + tool.name());
        }
        tools.put(tool.name(), tool);
        return this;
    }

    /** 按名字查工具，找不到返回 null。 */
    public Tool find(String name) {
        return tools.get(name);
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    /** 导出全部工具定义为 LLM 工具声明。 */
    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(t -> new ToolDefinition(t.name(), t.description(), t.schema()))
                .toList();
    }

    /** 内置六工具的组装收敛点。 */
    public static ToolRegistry of(Path workspace) {
        return new ToolRegistry()
                .register(new ReadFileTool(workspace))
                .register(new WriteFileTool(workspace))
                .register(new EditFileTool(workspace))
                .register(new BashTool(workspace))
                .register(new GlobTool(workspace))
                .register(new GrepTool(workspace));
    }
}