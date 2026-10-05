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

/** 工具注册表：管注册与查找，不做执行。 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /** 注册工具，重名抛错。 */
    public ToolRegistry register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalStateException("工具重名: " + tool.name());
        }
        tools.put(tool.name(), tool);
        return this;
    }

    public Tool find(String name) {
        return tools.get(name);
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    /** 导出为 LLM 工具声明。 */
    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(tool -> new ToolDefinition(tool.name(), tool.description(), tool.schema()))
                .toList();
    }

    /** 创建含全部内置工具的注册表。 */
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