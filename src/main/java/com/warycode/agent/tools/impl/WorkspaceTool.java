package com.warycode.agent.tools.impl;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** 文件类工具基类：取参、沙箱路径校验、遍历剪枝。 */
abstract class WorkspaceTool {

    private static final Set<String> IGNORED = Set.of(".git", "target", "node_modules", ".idea");

    protected final Path workspace;

    protected WorkspaceTool(Path workspace) {
        this.workspace = workspace;
    }

    /** 取必填字符串参数，缺失抛错。 */
    protected static String arg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return String.valueOf(v);
    }

    /** 取可选整数参数，未传用默认值；容忍模型把数字输出成字符串。 */
    protected static int argInt(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        if (v == null) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v).trim());
    }

    /** 沙箱校验：解析为 workspace 内的绝对路径，越界（../ 逃逸、外部绝对路径）抛错。 */
    protected Path resolve(String raw) {
        Path p = workspace.resolve(raw).normalize();
        if (!p.startsWith(workspace)) {
            throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + raw);
        }
        return p;
    }

    /** 路径任一段命中忽略目录(.git/target/node_modules/.idea)时返回 true。 */
    protected boolean isIgnored(Path relative) {
        for (Path seg : relative) {
            if (IGNORED.contains(seg.toString())) {
                return true;
            }
        }
        return false;
    }
}
