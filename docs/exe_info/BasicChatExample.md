# BasicChatExample 执行流程分析

> 文件位置：`agentscope-examples/documentation/quickstart/src/main/java/io/agentscope/examples/quickstart/BasicChatExample.java`
>
> 核心依赖：`ReActAgent`、`InMemoryMemory`、`Toolkit`、`OllamaChatModel`
>
> 本文档追踪 **memory** 和 **toolkit** 从构造到运行时完整调用链。

---

## 目录

1. [示例源码](#1-示例源码)
2. [构造阶段](#2-构造阶段)
3. [运行时执行流程](#3-运行时执行流程)
4. [memory 使用全览](#4-memory-使用全览)
5. [toolkit 使用全览](#5-toolkit-使用全览)
6. [完整调用链](#6-完整调用链)
7. [关键设计要点](#7-关键设计要点)

---

## 1. 示例源码

```java
// BasicChatExample.java
public class BasicChatExample {
    public static void main(String[] args) throws Exception {
        ExampleUtils.printWelcome("基础对话示例", "...");

        ReActAgent agent = ReActAgent.builder()
                .name("Assistant")
                .sysPrompt("你是一个有帮助的 AI 助手，请保持友好和简洁。")
                .model(OllamaChatModel.builder()
                        .modelName("llama3.2")
                        .baseUrl("http://localhost:11434")
                        .build())
                .memory(new InMemoryMemory())       // (1) 注入 memory
                .toolkit(new Toolkit())             // (2) 注入 toolkit
                .build();                           // (3) 构建

        ExampleUtils.startChat(agent);              // (4) 启动交互循环
    }
}
```

---

## 2. 构造阶段

### 2.1 Builder 默认值

```java
// ReActAgent.java Builder 字段定义
public static class Builder {
    private Toolkit toolkit = new Toolkit();           // 默认空 Toolkit
    private Memory memory = new InMemoryMemory();      // 默认 InMemoryMemory
    private int maxIters = 10;
    private boolean enableMetaTool = false;
    // ... 其他字段
}
```

BasicChatExample 显式传入了 `new InMemoryMemory()` 和 `new Toolkit()`（与默认值一致，但更明确）。

### 2.2 build() 内部流程

```
build()
│
├─ 1. Toolkit agentToolkit = this.toolkit.copy()
│     深拷贝：避免多个 Agent 共享同一 Toolkit 实例导致状态污染
│
├─ 2. registerToolsFromHooks(agentToolkit)
│     注册 hooks 声明的工具对象（本例无 hook，跳过）
│
├─ 3. if (enableMetaTool) agentToolkit.registerMetaTool()
│     本例 false，不注册元工具
│
├─ 4. if (longTermMemory != null) configureLongTermMemory()
│     本例未设置 LTM，跳过
│
├─ 5. if (!knowledgeBases.isEmpty()) configureRAG()
│     本例未设置 RAG，跳过
│
├─ 6. if (planNotebook != null) configurePlan()
│     本例未设置 PlanNotebook，跳过
│
├─ 7. if (skillBox != null) configureSkillBox()
│     本例未设置 SkillBox，跳过
│
└─ 8. return new ReActAgent(this, agentToolkit)
```

### 2.3 构造函数中的存储位置

| 组件 | 字段 | 类型 | 声明位置 |
|---|---|---|---|
| **memory** | `ReActAgent.this.memory` | `private final Memory` | ReActAgent.java:147 |
| **toolkit** | `StructuredOutputCapableAgent.this.toolkit` | `protected final Toolkit` | StructuredOutputCapableAgent.java:92 |

```java
// ReActAgent 构造函数 (line 170)
private ReActAgent(Builder builder, Toolkit agentToolkit) {
    super(builder.name, builder.description, builder.checkRunning,
          new ArrayList<>(builder.hooks), agentToolkit, ...);  // toolkit → 父类
    this.memory = builder.memory;                               // memory → 本类
    this.sysPrompt = builder.sysPrompt;
    // ...
}
```

---

## 3. 运行时执行流程

### 3.1 入口：`ExampleUtils.startChat(agent)`

```java
// ExampleUtils.java
public static void startChat(Agent agent) throws IOException {
    // 交互式循环
    while (true) {
        String input = readLine();                 // 读取用户输入
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(input).build())
                .build();

        agent.stream(userMsg, streamOptions)       // 流式调用 Agent
             .doOnNext(event -> { /* 打印流式输出 */ })
             .blockLast();
    }
}
```

### 3.2 `stream()` → `call()` 入口 (AgentBase)

```
agent.stream(userMsg)
  └─ AgentBase.call(List<Msg> msgs)                      ← AgentBase.java:222
       │
       ├─ acquireExecution()                              ← 获取执行许可（单实例串行）
       │
       ├─ beforeAgentExecution(msgs)                     ← ReActAgent override
       │    └─ 初始化 RuntimeContext / 重置 currentSystemMsg
       │
       ├─ notifyPreCall(msgs)                            ← PreCallEvent 钩子
       │    └─ 合并系统提示词 → 生成 systemMsg
       │
       ├─ doCall(msgs)                                   ← ReActAgent 实现
       │    │
       │    └─ [详见 3.3 节]
       │
       ├─ notifyPostCall(response)                       ← PostCallEvent 钩子
       │
       └─ releaseExecution()                             ← 释放执行许可
```

### 3.3 核心：`ReActAgent.doCall()`

```
doCall(msgs)
│
├─ getPendingToolUseIds()
│   └─ memory.getMessages()          ★ 读取记忆，检查是否有待处理工具
│     本例：空，无待处理工具
│
├─ addToMemory(msgs)                  ★ memory: 用户消息写入记忆
│   └─ msgs.forEach(memory::addMessage)
│
└─ executeIteration(0)
     │
     └─ reasoning(0, false)           ← 第 0 轮推理
          │
          ├─ checkInterruptedAsync()  ← 中断检查点
          │
          ├─ notifyPreReasoningEvent(memory.getMessages())
          │                           ★ memory: 读出全部历史构造 PreReasoningEvent
          │
          ├─ model.stream(modelInput, toolkit.getToolSchemas(), options)
          │                    ★ toolkit: 传入工具 Schema 给 LLM
          │    本例 toolkit.getToolSchemas() → []（空列表，无注册工具）
          │
          ├─ [流式处理 chunk]
          │   └─ notifyReasoningChunk(chunk, context)
          │
          ├─ notifyPostReasoning(msg)
          │
          ├─ memory.addMessage(msg)   ★ memory: 模型回复写入记忆
          │
          ├─ isFinished(msg)
          │   └─ msg 中有 ToolUseBlock? → 有 → 进入 acting()
          │     本例：无 ToolUseBlock（纯文本回复）→ 直接返回
          │
          └─ (仅当有工具调用时) acting(iter)
               │
               ├─ toolkit.setInternalChunkCallback(...)  ★ toolkit: 设置流式回调
               │
               ├─ toolkit.callTools(...)                  ★ toolkit: 执行工具
               │
               ├─ notifyPostActingHook(toolUse, result)
               │   └─ memory.addMessage(...)              ★ memory: 工具结果写入记忆
               │
               └─ executeIteration(iter + 1)  ← 进入下一轮
```

---

## 4. memory 使用全览

| # | 位置（行号） | 方法 | 操作 | 方向 |
|---|---|---|---|---|
| 1 | **367** | `doCall()` → `addToMemory(msgs)` | 用户输入消息 → memory | **写入** |
| 2 | **574** | `reasoning()` → `memory.getMessages()` | 读取全部记忆，传给 LLM 作为上下文 | **读取** |
| 3 | **611** | `reasoning()` → `notifyPostReasoning` → `memory.addMessage(msg)` | 模型推理结果 → memory | **写入** |
| 4 | **813** | `notifyPostActingHook()` → `memory.addMessage(...)` | 工具执行结果 → memory | **写入** |
| 5 | **840/869** | `summarizing()` → `memory.addMessage(...)` | 超时摘要 / 错误结果 → memory | **写入** |
| 6 | **1162** | `handleInterrupt()` → `memory.addMessage(...)` | 中断恢复消息 → memory | **写入** |
| 7 | **442-456** | `getPendingToolUseIds()` | 遍历记忆找出待处理工具调用 | **读取** |
| 8 | **894** | `prepareSummaryMessages()` | 读取全部记忆构造摘要输入 | **读取** |

### memory 数据流示意图

```
┌─────────────────────────────────────────────────────────┐
│                     InMemoryMemory                       │
│                    (CopyOnWriteArrayList<Msg>)           │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐  │
│  │ 用户消息  │→│ 模型回复  │→│ 工具调用  │→│ 工具结果│→│ ...
│  └──────────┘  └──────────┘  └──────────┘  └────────┘  │
│                                                          │
└─────────────────────────────────────────────────────────┘
         ↑ 写入 (addMessage)                ↓ 读取 (getMessages)
         │                                   │
    ReActAgent 各阶段                    每轮 reasoning() 时
    用户/模型/工具                       全部发给 LLM 作为输入
```

### `InMemoryMemory` 实现（`InMemoryMemory.java`）

```java
public class InMemoryMemory implements Memory {
    private final List<Msg> messages = new CopyOnWriteArrayList<>();

    public void addMessage(Msg message) { messages.add(message); }

    public List<Msg> getMessages() {
        return messages.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
}
```

- **线程安全**：使用 `CopyOnWriteArrayList`
- **无上限**：对话越长，memory 越大
- **无 summarization 截断**：模型输入随对话增长而线性增长

---

## 5. toolkit 使用全览

| # | 位置（行号） | 方法 | 操作 |
|---|---|---|---|
| 1 | **575** | `reasoning()` → `toolkit.getToolSchemas()` | 获取当前激活组的工具 Schema，传给 `model.stream()` 让 LLM 知道可用工具 |
| 2 | **675** | `acting()` → `toolkit.setInternalChunkCallback(...)` | 设置流式工具结果回调，转发到 ActingChunkEvent 钩子 |
| 3 | **761** | `executeToolCalls()` → `toolkit.callTools(...)` | 执行待处理的工具调用 |
| 4 | **311-315** | `saveTo()` → `toolkit.getActiveGroups()` | 持久化当前激活的工具组 |
| 5 | **347-349** | `loadFrom()` → `toolkit.setActiveGroups(...)` | 从会话恢复工具组状态 |

### `new Toolkit()` 内部结构

本例传入 `new Toolkit()`（无参构造），内部是：

```java
public Toolkit() {
    this(ToolkitConfig.defaultConfig());
}

public Toolkit(ToolkitConfig config) {
    this.config = config;
    this.methodInvoker = new ToolMethodInvoker(new DefaultToolResultConverter());
    this.schemaProvider = new ToolSchemaProvider(toolRegistry, groupManager);
    this.metaToolFactory = new MetaToolFactory(groupManager, toolRegistry);
    this.mcpClientManager = new McpClientManager(...);
    this.executor = new ToolExecutor(this, toolRegistry, groupManager, this.config);
}
```

因为没有注册任何 `@Tool` 方法，所以 `getToolSchemas()` 返回 `[]`。

---

## 6. 完整调用链

```
BasicChatExample.main()
│
├─ ReActAgent.builder().memory(...).toolkit(...).build()
│   ├─ Builder.build()
│   │   ├─ toolkit.copy()                          ← 深拷贝
│   │   ├─ registerToolsFromHooks()                ← 无 hook 工具
│   │   ├─ configureLongTermMemory()               ← 跳过
│   │   ├─ configureRAG()                          ← 跳过
│   │   ├─ configurePlan()                         ← 跳过
│   │   ├─ configureSkillBox()                     ← 跳过
│   │   └─ new ReActAgent(this, agentToolkit)      ← 构造完成
│   │
│   └─ ReActAgent 实例
│       ├─ memory = new InMemoryMemory()
│       └─ toolkit = copied Toolkit (空)
│
└─ ExampleUtils.startChat(agent)
    │
    └─ [循环] 读取用户输入 → agent.stream(userMsg)
         │
         └─ AgentBase.call(msgs)                           [AgentBase.java:222]
              │
              ├─ acquireExecution()
              │
              ├─ beforeAgentExecution(msgs)                 [ReActAgent.java:197]
              │   └─ runtime context 初始化
              │
              ├─ notifyPreCall(msgs)                        [AgentBase.java:~706]
              │   └─ PreCallEvent → 生成 systemMsg
              │
              ├─ doCall(msgs)                               [ReActAgent.java:361]
              │   │
              │   ├─ getPendingToolUseIds()                 [line 441]
              │   │   └─ memory.getMessages()               ★ memory 读
              │   │       → 无待处理工具
              │   │
              │   ├─ addToMemory(msgs)                      [line 534]
              │   │   └─ memory.addMessage(userMsg)         ★ memory 写
              │   │
              │   └─ executeIteration(0)                    [line 542]
              │       │
              │       └─ reasoning(0, false)                [line 556]
              │           │
              │           ├─ checkInterruptedAsync()
              │           │
              │           ├─ notifyPreReasoningEvent(memory.getMessages())
              │           │                                   ★ memory 读
              │           │
              │           ├─ model.stream(
              │           │     modelInput,
              │           │     toolkit.getToolSchemas(),     ★ toolkit 读 Schema
              │           │     options)
              │           │   └─ [流式 chunk 处理]
              │           │
              │           ├─ notifyPostReasoning(msg)
              │           │
              │           ├─ memory.addMessage(msg)          ★ memory 写
              │           │
              │           └─ isFinished(msg)
              │               └─ 无 ToolUseBlock → 直接返回 (本例)
              │
              ├─ notifyPostCall(response)                    [AgentBase]
              │
              └─ releaseExecution()                          [AgentBase]
```

---

## 7. 关键设计要点

### 7.1 ReAct 循环本质

```
            ┌─────────────────────────────────────┐
            │           ReAct 循环                  │
            │                                      │
            │    ┌──────────┐     ┌──────────┐     │
            │    │ 推理阶段  │────→│ 行动阶段  │     │
            │    │reasoning │     │  acting  │     │
            │    │          │←────│          │     │
            │    └──────────┘     └──────────┘     │
            │         │                │           │
            │         ▼                ▼           │
            │   memory 读出       memory 写入      │
            │   + LLM 调用       + toolkit 执行     │
            └─────────────────────────────────────┘
```

- **推理**：从 memory 读出全部历史，加上 toolkit Schema → 调 LLM
- **行动**：解析 LLM 返回的工具调用 → toolkit.callTools() 执行 → 结果写回 memory
- **循环**：直到 LLM 不再返回工具调用或达到 maxIters

### 7.2 memory 在循环中的角色

- **所有消息的集中存储**：用户输入、模型回复、工具请求、工具结果，全部写入 memory
- **每轮推理的全量输入**：`memory.getMessages()` 每次都返回全部消息，无窗口/截断
- **线程安全**：`CopyOnWriteArrayList`，读多写少的典型场景

### 7.3 toolkit 在循环中的角色

- **Schema 提供者**：推理阶段调用 `getToolSchemas()` 告知 LLM 有哪些工具可用
- **执行器**：行动阶段调用 `callTools()` 实际执行
- **本例为空**：`new Toolkit()` 未注册任何工具，LLM 不返回 ToolUseBlock，acting 不会执行

### 7.4 本例的特殊性

BasicChatExample 是一个**纯聊天示例**：

1. `Toolkit` 为空 → 没有可用工具 → LLM 永远不会返回工具调用
2. `isFinished(msg)` 始终为 true（无 ToolUseBlock）
3. `acting()` 永远不会执行
4. ReAct 循环每次只执行 **1 轮**就返回

但框架底层仍保持完整的 ReAct 循环结构，memory 和 toolkit 的集成机制与更复杂的示例（如 ToolCallingExample）完全一致。

### 7.5 相关文件索引

| 文件 | 路径 |
|---|---|
| BasicChatExample | `agentscope-examples/.../quickstart/BasicChatExample.java` |
| ReActAgent | `agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java` |
| AgentBase (call 入口) | `agentscope-core/.../agent/AgentBase.java` |
| StructuredOutputCapableAgent | `agentscope-core/.../agent/StructuredOutputCapableAgent.java` |
| InMemoryMemory | `agentscope-core/.../memory/InMemoryMemory.java` |
| Toolkit | `agentscope-core/.../tool/Toolkit.java` |
| ExampleUtils | `agentscope-examples/.../quickstart/ExampleUtils.java` |

---

> 生成时间：2026-06-02
>
> 代码版本：`agentscope-java` 1.1.0-SNAPSHOT
