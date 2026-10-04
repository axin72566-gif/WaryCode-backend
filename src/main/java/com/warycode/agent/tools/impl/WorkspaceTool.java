package com.warycode.agent.tools.impl;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 文件类工具的公共底座，只管"运行时"的公共事务。子类 execute() 的典型流程：
 *   Path p = resolve(arg(args, "path"));   // 取参 + 沙箱校验，得到工作目录内的绝对路径
 *   ...用 p 读文件 / 写文件 / 遍历...
 * 三组能力，按流程顺序：
 * 1. 取参   arg / argInt     从模型给的参数表取值，容忍类型漂移（数字被输出成字符串）
 * 2. 沙箱   resolve          把路径关进 workspace，拒绝 ../ 逃逸和外部绝对路径
 * 3. 剪枝   isIgnored        glob/grep 遍历目录时跳过 .git/target/node_modules/.idea
 */
abstract class WorkspaceTool {

    /** 遍历时跳过的目录：版本库、构建产物、依赖目录。 */
    private static final Set<String> IGNORED = Set.of(".git", "target", "node_modules", ".idea");

    protected final Path workspace;

    protected WorkspaceTool(Path workspace) {
        this.workspace = workspace;
    }

    /** 取必填字符串参数；缺失时抛错，错误信息经 ToolExecutor 转给模型让它重试。 */
    protected static String arg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return String.valueOf(v);
    }

    /** 取可选整数参数，没传就用默认值；模型可能把数字输出成字符串，统一转一下。 */
    protected static int argInt(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        if (v == null) {
            return def;
        }
        return Integer.parseInt(String.valueOf(v).trim());
    }

    /** 沙箱校验：解析成绝对路径并归一化，必须仍落在 workspace 内，否则报错。 */
    protected Path resolve(String raw) {
        Path p = workspace.resolve(raw).normalize();
        if (!p.startsWith(workspace)) {
            throw new IllegalArgumentException("路径越界(仅允许工作目录内): " + raw);
        }
        return p;
    }

    /** 相对路径中任一段命中忽略目录即返回 true，供 glob/grep 遍历时跳过。 */
    protected boolean isIgnored(Path relative) {
        for (Path seg : relative) {
            if (IGNORED.contains(seg.toString())) {
                return true;
            }
        }
        return false;
    }
}
