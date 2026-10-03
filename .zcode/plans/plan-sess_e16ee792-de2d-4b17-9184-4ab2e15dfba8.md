# WaryCode Agent 实现计划书
—— 基于 OpenAI Java SDK（openai-java 4.63.3）的基础 Coding Agent

## 一、项目定位与目标

在空骨架项目（Java 21 + Maven，无 Spring，已有 openai-java 4.63.3 依赖）上，从零实现一个运行在用户本机的交互式编码智能体：终端 REPL 中输入自然语言任务，agent 通过六个工具（readFile / writeFile / editFile / Bash / glob / grep）在工作目录内自主完成"定位代码 → 读取 → 修改 → 编译验证"的完整闭环。

本次要达成的四个目标：
1. **功能可用**：多轮对话 + 工具调用循环 + 沙箱安全，能真实完成小型编码任务；
2. **架构可演进**：LLM SDK 隔离在适配层（防腐层），换 SDK/供应商不动业务；
3. **工具可插拔**：注册中心 + 执行器框架，新增工具一个类 + 一行注册；
4. **实现克制**：不引入新依赖、不做流式/审批/持久化（架构预留，见扩展路线）。

## 二、架构总览

### 2.1 分层结构

```
┌────────────────────────────────────────────────────────┐
│ 入口层   Main(REPL) ←→ AgentConfig(config.properties)   │
├────────────────────────────────────────────────────────┤
│ Agent 层  CodingAgent（循环编排：LLM ↔ 工具 之间调度）      │
│     │ LLM 端口                     │ 工具管道            │
│     ▼                            ▼                    │
│ ChatClient(接口)            ToolExecutor(执行器)         │
│     ▲ 唯一实现                     ▼                    │
│ OpenAiChatClient            ToolRegistry(注册中心)        │
│ （唯一 import com.openai.*）        ▼                    │
│                             Tool 接口 ← 6 个内置工具      │
├────────────────────────────────────────────────────────┤
│ 内部模型  llm/model/*（record，不含任何 SDK 类型）          │
└────────────────────────────────────────────────────────┘
```

### 2.2 各层职责与依赖规则

- **入口层**：装配所有组件、提供人机界面（REPL）。除了 main 方法与读入循环，不含任何逻辑。
- **Agent 层**：唯一"知道整个流程"的地方——把用户意图转成 LLM 请求、把工具结果回填、决定何时结束一轮。它只依赖 ChatClient 接口与 ToolExecutor，既不知道 OpenAI 的存在，也不知道具体有哪些工具。
- **LLM 适配层**：ChatClient 接口（内部协议）+ OpenAiChatClient 实现。所有 SDK 类型（参数构造、响应解析）的转换收敛在适配器一个文件里。
- **工具框架层**：Tool 契约 + ToolRegistry（注册中心，管"有哪些工具"）+ ToolExecutor（执行器，管"怎么调用"）。框架不依赖 LLM 层，只有一处例外：Registry 导出 ToolDefinition（内部模型），供请求携带。
- **内部模型层**：纯 record，被 Agent 层、适配层、工具接缝共同引用，是全项目的"通用语言"。

依赖方向严格单向向下；`com.openai.*` 的 import 只允许出现在 `llm/openai/OpenAiChatClient.java`（实现后用 grep 校验）。

### 2.3 一次完整交互的时序走查（以"把 Hello.java 里的 hello 改成 hi 并重新运行"为例）

```
用户输入 ──► CodingAgent: history 注入 System+User
          ──► ChatClient.complete(ChatRequest)          [适配层→OpenAI]
          ◄── ChatResponse(toolCalls=[readFile])         [模型决定先读文件]
          ──► ToolExecutor.execute(ToolCall)
          │    路由→解析参数→沙箱校验→执行→截断
          │    ToolResult(content=带行号的文件内容)
          ──► history 回填 AssistantMessage + ToolMessage
          ──► ChatClient.complete(...)                    [模型看到文件内容]
          ◄── ChatResponse(toolCalls=[editFile])
          ──► ToolExecutor 执行 editFile ──► 回填 ──► complete(...)
          ◄── ChatResponse(toolCalls=[Bash])              [模型编译验证]
          ──► 执行 Bash ──► 回填 ──► complete(...)
          ◄── ChatResponse(toolCalls=[], text="已将…改为 hi，运行输出…")
          ──► REPL 打印最终答复，本轮结束（history 保留，下一轮继续累积）
```

期间任何工具出错（路径越界、匹配不唯一、命令超时）都只是回传一条 isError 的 ToolMessage，模型据此自我纠正，会话不中断。

## 三、文件清单

```
pom.xml                          # 改：groupId→com.warycode；加 exec-maven-plugin
config.properties.example        # 新：配置模板（进 git）
.gitignore                       # 改：追加 config.properties
src/main/java/com/warycode/agent/
├── Main.java                    # 入口：装配 + REPL
├── AgentConfig.java             # 配置加载与校验
├── agent/CodingAgent.java       # 核心循环
├── llm/ChatClient.java          # LLM 端口接口
├── llm/model/                   # 内部模型（8 个 record 文件）
│   ├── ChatMessage.java         #   sealed 接口
│   ├── SystemMessage / UserMessage / AssistantMessage / ToolMessage .java
│   ├── ToolDefinition.java  ToolCall.java
│   └── ChatRequest.java     ChatResponse.java
├── llm/openai/OpenAiChatClient.java   # SDK 适配器（唯一 com.openai.* 触点）
└── tools/
    ├── Tool.java                # 工具契约
    ├── ToolResult.java          # 执行结果（isError + content）
    ├── ToolRegistry.java        # 注册中心
    ├── ToolExecutor.java        # 执行器
    └── impl/
        ├── WorkspaceTool.java   # 抽象基类：沙箱校验/剪枝/取参/schema 辅助
        ├── ReadFileTool.java  WriteFileTool.java  EditFileTool.java
        └── BashTool.java      GlobTool.java       GrepTool.java
```

共 22 个 Java 文件（多数为 10 行以内的 record）+ 2 个配置文件 + pom/.gitignore 修改。

## 四、各文件职责与设计解析

### 4.1 pom.xml（修改）
groupId 从占位符 `org.example` 改为 `com.warycode`；Java 21 与 UTF-8 属性保持不变；依赖仅有 openai-java 4.63.3——Jackson（ObjectMapper）作为它的传递依赖直接使用，不为省一个声明而引入；新增 `exec-maven-plugin:3.5.0` 指定 mainClass，这样 `mvn compile exec:java` 一条命令即可运行，免去手工拼 classpath 或打 shade 包的复杂度。

### 4.2 config.properties.example（新建）与 .gitignore（修改）
模板四项配置：`api.key`（必填，OpenAI 或兼容端点的密钥）、`base.url`（默认官方 `https://api.openai.com/v1`，可指向 DeepSeek/GLM/Ollama 等 OpenAI 兼容端点——adapter 只需换 baseUrl 即通吃）、`model`（默认 gpt-5.5）、`workspace`（文件工具的沙箱根，留空表示程序启动目录）。真实文件 `config.properties` 追加进 .gitignore，密钥永不进版本库；模板进 git，新环境复制即用。配置采用"手动填文件"而非环境变量，符合本机使用习惯，也让多套配置可以并存（改文件即切换）。

### 4.3 AgentConfig.java
一个 record（apiKey / baseUrl / model / workspace）+ 静态工厂 `load()`，用 JDK 自带 `java.util.Properties` 读取，无新依赖。行为细节：首次运行若 config.properties 不存在，自动从模板复制一份并提示"填写 api.key 后重新运行"，降低第一次使用的摩擦；`api.key` 为空直接抛带指引的异常退出，不等到第一次调用才在网络错误里猜原因；其余三项缺省取默认值。workspace 统一做 `toAbsolutePath().normalize()` 归一化——这是后续所有沙箱校验的基准，必须在此处一次性规范。

### 4.4 llm/ChatClient.java（端口接口）
只有一个方法：`ChatResponse complete(ChatRequest)`。这是全项目对"LLM"的全部抽象——非流式、一问一答。刻意保持最小：将来接流式输出时派生 `StreamingChatClient` 子接口，接多模态时扩展 ChatRequest 内容类型，都不需要触碰现有实现与调用方。抽象越小，防腐层越薄，替换成本越低。

### 4.5 llm/model/（内部协议，8 个 record）
- **ChatMessage**：sealed interface，四个实现——`SystemMessage(content)`、`UserMessage(content)`、`AssistantMessage(text, toolCalls)`（text 可为 null：纯工具调用轮模型往往不说话）、`ToolMessage(toolCallId, content, isError)`（isError 标记工具执行失败，是自愈闭环的载体）。
- **ToolDefinition(name, description, schema)**：schema 用纯 `Map<String,Object>` 表达 JSON schema 片段（type/properties/required），不引入任何 schema 库，也不绑定 SDK 的参数类型。
- **ToolCall(id, name, argumentsJson)**：argumentsJson 保留模型返回的原始 JSON 字符串——这是各供应商协议的最大公约数，解析时机推迟到工具边界（执行器里做），模型层保持"原样透传"。
- **ChatRequest(model, messages, tools)** / **ChatResponse(text, toolCalls, finishReason)**：一次请求/响应的完整快照。

设计意图：全部不可变 record、零 SDK 类型，构成全项目的"通用语言"。sealed 的价值在编译期——适配层用 switch 模式匹配做消息转换是 exhaustive 的，将来新增消息类型（如 ImageMessage）时，编译器会强制适配层同步补分支，漏改直接编译失败而不是运行期静默丢失消息。

### 4.6 llm/openai/OpenAiChatClient.java（适配器，唯一 SDK 触点）
实现 ChatClient；构造时以 apiKey + baseUrl 构建 OkHttp 客户端。内部三个转换环节，全部为私有方法，外部不可见：
1. **出向消息转换**：switch 四分支。System/User 直接映射为对应 MessageParam；Assistant 需要还原 text（非空才携带）与逐个 toolCall（id + name + argumentsJson 重建为 SDK 的函数调用结构）；Tool 映射为 toolCallId + content，若 isError 则内容前缀 `[工具执行出错]`——OpenAI 协议没有错误标记字段，用前缀保证模型能明确感知失败。
2. **出向工具转换**：ToolDefinition → SDK 的 function tool。schema 的 Map 逐键塞入 SDK 的 FunctionParameters 构造器（该版本 SDK 无便捷工厂，只能逐键 put，这是 4.63.3 反编译确认过的形态）。
3. **入向响应转换**：取 choices 首项的 message——content 是 Optional 映射为可空 text，toolCalls 逐个取出 id/name/arguments 组装为内部 ToolCall 列表，finishReason 一并带出（便于将来按停止原因做分支）。

设计意图：SDK 的任何变化（像 4.63.3 这种 Kotlin 重写式的大改）都被挡在这一个文件里。已反编译本地 jar 核实全部关键签名；即便实现时个别 builder 方法名有出入，修正也只发生在此文件。将来换 Anthropic/langchain4j，新增一个 adapter 实现类 + Main 里换一行装配即可。

### 4.7 tools/Tool.java 与 ToolResult.java（框架契约）
Tool 接口五个成员：`name()`（唯一标识）、`description()`（给模型看的能力说明，写得好坏直接影响模型调用准确率）、`schema()`（参数的 JSON schema）、`execute(args)`（核心逻辑，返回文本结果，允许抛异常——异常是框架要处理的正常输入而非事故）、`timeoutSeconds()`（默认 120 秒，可按工具覆写）。
ToolResult 是 record(isError, content) 加 ok/error 两个语义工厂。设计意图：把"做什么"（工具实现者的全部责任）与"怎么调用"（执行器的责任）彻底分开——实现者不需要考虑超时、重试、日志、截断，框架天然赋予所有工具这些能力。

### 4.8 tools/ToolRegistry.java（注册中心）
LinkedHashMap 存储（保持注册顺序，导出给模型的工具列表稳定）；`register()` 重名 fail-fast 抛异常（启动期暴露配置错误，好过运行期静默覆盖）；`byName()` 供执行器路由；`names()` 用于未知工具报错时列出可用清单；核心是 `definitions()`——把注册表一键导出为 `List<ToolDefinition>`，这是工具框架与 LLM 层的唯一接缝：**注册即生效**，新工具在 CodingAgent 的下一次请求中自动出现，全程零配置。`withDefaults(workspace)` 静态工厂收敛六个内置工具的组装，Main 不逐个 new。注册中心自身不做任何执行逻辑，职责单一。

### 4.9 tools/ToolExecutor.java（执行器，统一调用管道）
`execute(ToolCall) → ToolResult`，六段管道依次为：
1. **流水日志**：打印工具名 + 参数单行摘要（120 字符截断），终端实时可见 agent 在做什么；
2. **路由**：registry 查找，未知工具名返回错误 ToolResult 并附可用工具列表（模型会自我纠正调用）；
3. **参数解析**：Jackson 把 argumentsJson 解析为 Map，解析失败同样转为错误 ToolResult；
4. **超时控制**：Java 21 虚拟线程池（`newVirtualThreadPerTaskExecutor`，近乎零成本）提交执行，`Future.get(timeoutSeconds())` 兜底——任何工具卡死都会被切断；
5. **异常兜底**：捕获一切异常（含沙箱越界、IO 失败、参数缺失）统一转错误 ToolResult——工具崩溃永远击不穿 agent 循环；
6. **结果规范化**：超过 16KB 截断（防上下文爆炸），done() 统一打印 ✓/✗ 摘要。

设计意图：横切关注集中一处、对所有工具一视同仁；这也是将来插入"危险命令审批钩子"的天然位置（管道中加一个环节即可，见扩展路线）。

### 4.10 tools/impl/WorkspaceTool.java（沙箱基类）
五个文件类工具的抽象底座，集中四件事：
- `resolve(raw)`：**沙箱校验核心**。workspace.resolve(raw) 后 normalize，再校验结果必须仍以 workspace 开头，否则抛"路径越界"——`../` 逃逸、绝对路径注入都被拦下；
- `ignored(relPath)`：遍历剪枝，跳过 `.git / target / node_modules / .idea`，避免 glob/grep 扫出一堆噪音还浪费上下文；
- `objectSchema(...)`：schema 快捷构造辅助（传入必填列表 + "参数名/类型/说明"三元组），六个工具的 schema 声明压缩为一行，可读且不易写错；
- `arg()/argInt()`：宽容取参——模型输出类型可能漂移（数字给成字符串），统一 toString 后转换，缺必填参数抛出带参数名的清晰错误。

### 4.11 六个内置工具

| 工具 | 参数 | 行为与上限 |
|---|---|---|
| readFile | path(必)；offset/limit(可选，1 起行号) | UTF-8 读取；输出带行号前缀，与 editFile 形成"看行号→精确改"配合；超 64KB 截断并提示用 offset/limit 分段读 |
| writeFile | path(必)、content(必) | 整体覆盖写；父目录自动创建；适合新建与重写 |
| editFile | path、old_string、new_string(均必) | 精确字符串替换；匹配 0 次或 ≥2 次都报错（要求唯一），报错文案明确引导"先 readFile 确认"——自愈闭环的典型一环 |
| Bash | command(必)、timeout(可选，默认 60s) | workspace 内执行（Windows 用 `cmd.exe /c`，其余 `sh -c`）；合并 stdout/stderr；虚拟线程异步读取输出防管道死锁；超时 destroyForcibly；返回退出码 + 输出 |
| glob | pattern(必)、path(可选) | JDK 原生 PathMatcher；遍历深度限 8；按修改时间倒序（最新改动优先，符合编码场景直觉）；限 200 条相对路径 |
| grep | pattern(必，正则)、path/include/max 可选 | 逐行正则匹配；include 用 glob 过滤文件名（如 *.java）；输出 `路径:行号: 内容`；限 100 条；二进制/编码异常文件静默跳过 |

补充两点：Bash 覆写 timeoutSeconds() 为 300 作为执行器层兜底，内部又自管默认 60s 进程级超时并 kill 子进程——双层超时各司其职（线程超时不等于进程被杀，Windows 上尤其如此）。所有文件类工具的路径都经 `resolve()` 校验，遍历类工具都经 `ignored()` 剪枝，这是沙箱在工具侧的全部落点。

### 4.12 agent/CodingAgent.java（核心循环）
持有 chatClient、executor、registry、cfg 与 `List<ChatMessage> history`（多轮记忆，REPL 期间常驻）。`run(input)` 单轮流程：history 为空时注入 SystemMessage（角色设定 + 工作目录 + 六条工具准则：修改前先 readFile、局部修改用 editFile 而非整文件重写、glob/grep 定位代码、Bash 编译验证、大文件分段读、完成后简述改动）；追加 UserMessage 后进入循环——每次以"history 快照 + registry.definitions()"发起 complete()，把返回的 AssistantMessage 原样入历史；无 toolCalls 即返回文本收尾，有则逐个交执行器，ToolMessage 回填后继续。循环上限 24 次，防模型陷入死循环空烧 token；达上限返回提示语，用户可继续输入引导。`clear()` 清空历史（System prompt 下轮自动重建）。分工边界：工具异常已在执行器兜底，这里只会遇到 LLM 调用异常（网络/鉴权/限流），向上抛给 REPL 处理。整个类不到 40 行核心逻辑——转换在适配层、管道在执行器、发现靠注册中心，agent 只剩纯调度。

### 4.13 Main.java（入口 + REPL）
装配根：Config → Adapter → Registry(withDefaults) → Executor → Agent，五行完成组装，体现"入口只装配不做事"。REPL：UTF-8 BufferedReader 读 stdin（兼容管道/IDE 运行），`> ` 提示符，启动时打印 model 与 workspace 让用户确认生效配置；支持 `/exit` `/quit`（退出）、`/clear`（清空对话重新开始）、`/help`；EOF（Ctrl+Z/D）优雅退出；`agent.run()` 抛出的异常只打印 `[LLM 调用失败] + 原因`，REPL 不退出、历史保留——网络抖动后重发同样输入即可重试，这是"长会话不因一次错误报废"的最后一道保障。

## 五、重要设计点

1. **防腐层（本计划第一原则）**：`import com.openai` 只出现在适配器。换供应商、换 SDK、SDK 大版本升级，影响面都收敛在一个文件；grep 校验作为验收项固化这一约束。
2. **注册中心与执行器分工**：Registry 答"有哪些工具"，Executor 答"怎么调用"。新增工具 = 实现 Tool + register 一行，模型下次请求即感知；删除/禁用工具同理。管道与清单互不纠缠。
3. **双层超时**：执行器虚拟线程兜底（默认 120s，Bash 300s）+ Bash 进程级超时（destroyForcibly）。前者保 agent 不被卡死，后者保系统资源真正释放。
4. **错误自愈闭环**：工具错误 → ToolResult(isError) → `[工具执行出错]` 前缀回传 → 模型重读/换参数重试。错误是给模型的反馈信号，而不是给人的中断弹窗。
5. **沙箱三件套**：路径 normalize+startsWith 防逃逸；遍历剪枝去噪；多层截断（结果 16KB / 读文件 64KB / grep 100 条 / glob 200 条）防上下文爆炸——这是长期使用成本（token）与功能完整性的平衡点。
6. **多轮记忆**：history 完整保留 Assistant/Tool 消息链，模型能看到自己每一轮的工具调用与结果，跨轮任务（"继续，把测试也补上"）才有连续性。
7. **sealed 消息模型**：switch exhaustive，编译期强制适配层与消息模型同步演进，杜绝"新增类型漏改适配"这类静默 bug。
8. **克制的依赖策略**：全项目仅 openai-java 一个直接依赖，schema 用 Map、配置用 Properties、JSON 用传递的 Jackson——框架越薄，将来迁移越容易。

## 六、使用说明

**首次配置**：`cp config.properties.example config.properties`，填写 api.key（如用兼容端点则改 base.url 与 model），可选指定 workspace。

**启动**：`mvn compile exec:java`（Windows 控制台中文乱码先执行 `chcp 65001`）。启动横幅显示生效的 model 与 workspace；`> ` 提示符下输入自然语言任务；工具调用过程实时打印 `→ 工具名 参数` 与 `✓/✗ 摘要`。

**REPL 命令**：`/help` 帮助；`/clear` 清空对话；`/exit`、`/quit` 或 EOF 退出。

## 七、验证方案

1. **编译**：`mvn compile` 通过。
2. **架构约束**：grep 全工程，确认 `import com.openai` 仅出现在 `llm/openai/OpenAiChatClient.java`。
3. **框架冒烟（不依赖模型，不花钱）**：临时 main 直接走 Registry→Executor 管道调用六个工具——正常读写/替换/执行命令/glob/grep 各一例；`path=../xxx` 越界被拒；未知工具名返回可用列表；人为抛异常的工具被兜底为错误 ToolResult。
4. **端到端（配置好后）**：真实对话走查三类任务——① 新建：'在 demo 目录创建 Hello.java 并编译运行'；② 修改：'把 Hello.java 的输出改成 hi 再运行'（验证 readFile→editFile→Bash 链）；③ 搜索：'项目里哪些地方用了 Jackson'（验证 glob/grep）。同时验证多轮上下文（第二轮说"继续"仍记得上文）与 /clear。

## 八、后续扩展路线（架构已预留，本次不做）

- **多供应商**：config 加 provider 字段，新增 `llm/<provider>/XxxChatClient` 实现即可，Main 按配置装配；
- **流式输出**：ChatClient 派生 StreamingChatClient 子接口，仅适配层实现，agent 循环不变；
- **危险操作审批**：Executor 管道加一个"确认钩子"环节（Bash/写操作前 REPL 询问 y/n），正好插在日志与执行之间；
- **会话持久化**：history 序列化到 .warycode/sessions/，重启续聊；
- **工具测试**：六工具均为纯类，补 JUnit（届时引测试依赖）。

## 九、风险与对策

- **SDK builder 细节与设计有出入**：关键签名已反编译 4.63.3 jar 逐一核实；若实现期编译报错，修正仅限适配器一个文件，不影响其他层。
- **模型不遵循 schema（类型漂移/缺参）**：WorkspaceTool 宽容取参 + 明确报错文案，模型可自愈；执行器异常兜底保底不崩。
- **上下文膨胀（长会话 token 成本）**：当前策略为多层截断；若仍不够，扩展路线中可加"历史压缩"（旧轮工具结果摘要化）。
- **Windows 控制台编码**：JDK 18+ 默认 UTF-8，代码全程显式 UTF-8；乱码时 `chcp 65001` 已在使用说明中提示。