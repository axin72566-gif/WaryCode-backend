# WaryCode

一个运行在用户本机的命令行编码 Agent，纯 Java 21 实现，零框架——不含 Spring/Web 框架，仅三个直接依赖（OpenAI Java SDK、Hutool、zt-exec）。核心是一个「LLM ↔ 工具」调度循环：把自然语言任务交给模型，模型通过 readFile / editFile / Bash 等工具在你的工作目录里读代码、改代码、编译验证，直到任务完成。

## 特性

- **Agent 调度循环**：调 LLM → 解析工具调用 → 执行并回填结果 → 循环直到模型返回文本（单轮上限 24 次迭代，防止失控）
- **6 个内置工具**：文件读写、精确编辑、命令执行、文件名/内容搜索，全部限制在工作目录内
- **技能（Skills）**：`skills/` 下放 SKILL.md 即可扩展能力，模型按任务描述自主取用
- **Hooks**：`hooks.json` 声明外部子进程钩子，在工具执行前后、任务提交/结束等 5 个时机拦截或质检，exit 2 即拦截并把原因回传模型
- **上下文管理**：单条工具结果 16KB 截断 + 旧工具结果自动替换为占位符（保留 toolCallId 配对），对话历史有结构性体积上限
- **OpenAI 兼容端点**：可接 OpenAI 官方，也可指向 DeepSeek / GLM / Ollama 等兼容端点

## 快速开始

环境要求：JDK 21+、Maven 3.x。

1. 在项目根目录创建 `config.properties`（已被 gitignore，不会误提交）：

   ```properties
   # API Key（必填）
   api.key=sk-xxxxxxxx

   # API 端点：留空使用 OpenAI 官方；可指向 OpenAI 兼容端点
   base.url=https://api.deepseek.com

   # 模型名，按所用端点支持的模型填写
   model=deepseek-chat
   ```

2. 编译并启动：

   ```bash
   mvn compile
   mvn exec:java -Dexec.mainClass=com.warycode.agent.Main
   ```

3. 启动后看到横幅即就绪，直接输入任务（如「把 Repl 的错误提示改成英文」），`/help` 查看命令。

注意：工作区 = 启动时所在的目录（文件工具只能访问其中文件），`config.properties`、`skills/`、`hooks.json` 均按启动目录的相对路径读取，需从项目根启动。

## REPL 命令

| 命令 | 作用 |
| --- | --- |
| `/help` | 查看可用命令 |
| `/clear` | 清空对话历史（System prompt 下轮自动重建） |
| `/history` | 查看历史占用：消息条数、约多少 KB、工具结果完整/已省略分布 |
| `/exit` `/quit` | 退出 |

## 内置工具

| 工具 | 说明 |
| --- | --- |
| `readFile` | 读取工作目录内文本文件，带行号；大文件用 offset/limit 分段读 |
| `writeFile` | 整体覆盖写入，父目录不存在自动创建 |
| `editFile` | 精确替换：old_string 须唯一，适合局部修改 |
| `Bash` | 执行 shell 命令（直接写原始命令），60 秒超时并杀进程树 |
| `glob` | 按 glob 模式查文件名，按修改时间倒序 |
| `grep` | 正则搜索文件内容，输出 `路径:行号: 内容` |
| `skill` | 取技能正文；不传 name 时列出全部技能名录（有技能时自动注册） |

## 技能（Skills）

启动时扫描 `skills/` 下（最深 3 层）的 `SKILL.md`，解析 frontmatter 中的 `name` / `description`（name 缺省用目录名），把名录注入系统提示；模型遇到匹配任务时先调 `skill` 工具取完整指引，再按指引工作。

新增技能：建目录 `skills/my-skill/`，写一个 SKILL.md：

```markdown
---
name: my-skill
description: 一句话说清什么任务该用本技能——描述质量直接决定触发准确率
---

# 技能标题

（给模型看的工作流程、约束、输出格式……）
```

单个文件损坏只告警跳过，不中断启动；改动需重启生效。内置示例：`skills/project-briefing/`（项目简报生成）。

## Hooks

`hooks.json` 声明外部子进程钩子，无需写 Java、任何语言的单文件脚本都能当 hook：

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "command": "java scripts/hooks/GitGuard.java",
        "timeout": 20,
        "onFailure": "block"
      }
    ],
    "PostToolUse": [
      {
        "matcher": "writeFile|editFile",
        "command": "java scripts/hooks/CompileCheck.java",
        "timeout": 180
      }
    ]
  }
}
```

字段：`matcher` 为工具名正则（仅对两个工具事件生效）；`timeout` 秒数（默认 30）；`onFailure` 设为 `block` 时，hook 崩溃/超时/非约定退出码也从默认放行（fail-open）改为拦截。

### 事件

| 事件 | 时机 | 拦截效果 |
| --- | --- | --- |
| `SessionStart` | 启动横幅打印后 | 仅提示 |
| `UserPromptSubmit` | 用户提交任务时（payload 带 `prompt`） | 任务不执行 |
| `PreToolUse` | 工具执行前（payload 带 `tool_name` / `tool_input`） | 工具不执行，原因回传模型 |
| `PostToolUse` | 工具成功执行后 | 意见追加到结果末尾，供模型当轮自纠 |
| `Stop` | 一轮回答完成后 | v1 仅提示 |

### hook 协议

- 输入：事件 JSON 写入子进程 stdin（`hook_event_name` / `session_id` / `workspace` / `tool_name` / `tool_input` 等），数据只走 stdin、不做命令行模板插值，规避 shell 注入
- `exit 0`：放行，stdout 以暗色日志打出
- `exit 2`：拦截，stderr 内容作为原因回传模型
- 其他退出码 / 超时 / 崩溃：默认放行并告警（fail-open）

内置示例：`scripts/hooks/GitGuard.java`（拦截 force push、`git clean -f`、经 shell 改写 `hooks.json`/`config.properties`）与 `scripts/hooks/CompileCheck.java`（Java 源码被改后立即 `mvn test-compile`，编译错误当轮回传让模型立刻修复）。

## 上下文管理

对话历史只增不减、每次请求全量重发，因此做了两级瘦身：

1. **单条截断**：每个工具结果超过 16KB 即截断（`ToolExecutor`）
2. **历史裁剪**：最近 10 条工具结果之外的旧内容替换为占位符（`HistoryTrimmer`）——只换内容不删消息，toolCallId 原样保留，OpenAI 协议的 tool_call 配对不受影响；模型需要时仍可按上方调用参数重新执行

`/history` 可随时查看历史体积与省略分布。

## 项目结构

```
src/main/java/com/warycode/agent/
├── Main.java                  # 装配组件并启动 REPL
├── Repl.java                  # 命令行交互循环（/ 命令分发）
├── AgentConfig.java           # 读取 config.properties
├── Ansi.java                  # 终端着色
├── agent/
│   ├── CodingAgent.java       # 核心调度循环
│   └── HistoryTrimmer.java    # 旧工具结果占位符化
├── llm/
│   ├── ChatClient.java        # LLM 抽象接口
│   ├── model/                 # 消息与请求响应模型（sealed）
│   └── openai/OpenAiChatClient.java  # OpenAI SDK 实现（全工程仅此文件依赖 SDK）
├── tools/
│   ├── Tool.java              # 工具接口
│   ├── ToolRegistry.java      # 注册与查找
│   ├── ToolExecutor.java      # 执行入口：hook、截断、异常兜底
│   └── impl/                  # 6 个内置工具
├── skills/                    # 技能装载（SKILL.md frontmatter 解析）
└── hooks/                     # hook 装载与子进程执行器
scripts/hooks/                 # 示例 hook（GitGuard / CompileCheck）
skills/                        # 技能目录（含 project-briefing 示例）
hooks.json                     # hook 声明
config.properties              # 本地配置（gitignore，需自行创建）
```

## 依赖

| 依赖 | 用途 |
| --- | --- |
| `com.openai:openai-java` | OpenAI 兼容端点调用，仅 `OpenAiChatClient` 一处使用 |
| `cn.hutool:hutool-all` | JSON 解析、字符串工具 |
| `org.zeroturnaround:zt-exec` | 子进程执行（Bash 工具与 hook）：输出捕获、超时杀进程树 |
