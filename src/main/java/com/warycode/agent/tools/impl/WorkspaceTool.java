package com.warycode.agent.tools.impl;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件类工具的公共底座：沙箱校验、遍历剪枝、宽容取参、schema 快捷构造。
 */
abstract class WorkspaceTool {

    static final Set<String> IGNORED = Set.of(".git", "target", "node_modules", ".idea");

    protected final Path workspace;

    protected WorkspaceTool(Path workspace) {
        this.workspace = workspace;
    }

    /** 沙箱校验：归一化后必须仍落在 workspace 内，拒绝 .. 逃逸与外部绝对路径。 */
    protected Path resolve(String raw) {
        Path p = workspace.resolve(raw).normalize();
        if (!p.startsWith(workspace)) {
            throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + raw);
        }
        return p;
    }

    /** 遍历剪枝：跳过版本库/构建产物目录。 */
    protected boolean ignored(Path relative) {
        for (Path seg : relative) {
            if (IGNORED.contains(seg.toString())) {
                return true;
            }
        }
        return false;
    }

    /** schema 快捷构造：required + ("参数名","类型","说明") 三元组序列。 */
    protected static Map<String, Object> objectSchema(List<String> required, Object... triples) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < triples.length; i += 3) {
            props.put((String) triples[i], Map.of(
                    "type", triples[i + 1],
                    "description", triples[i + 2]));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", required);
        return schema;
    }

    /** 宽容取参：模型输出类型可能漂移（数字给成字符串），统一 toString；缺参报清晰错误。 */
    protected static String arg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return String.valueOf(v);
    }

    protected static Integer argInt(Map<String, Object> args, String key, Integer def) {
        Object v = args.get(key);
        return v == null ? def : Integer.valueOf(String.valueOf(v).trim());
    }
}
