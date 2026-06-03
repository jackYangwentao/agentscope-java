# 多 Agent 工作流示例

本模块演示了**自定义工作流**模式：您可以使用 **Spring AI Alibaba StateGraph** 定义自己的执行流程，
对图拥有完全控制权 —— 顺序步骤，混合**确定性**（例如向量搜索、数据库）和 **agentic**
（LLM/agent）节点。当标准多 agent 模式（Pipeline、Routing、Subagents）不适用，
或者您需要显式控制每个步骤的多阶段处理时，请使用此模式。

**包含两个完整示例：**

| 示例 | 流程 | 节点 | 使用的工具 |
|---------|------|-------|------------|
| **RAG agent** | 重写 → 检索 → 准备 → agent | 3 个确定性 + 1 个 agentic | `get_latest_news` |
| **SQL agent** | 列表 → 获取模式 → 生成查询 | 2 个确定性 + 1 个 LLM + 1 个 agentic | `sql_db_list_tables`, `sql_db_schema`, `sql_db_query` |

两者均使用 **Spring AI Alibaba StateGraph** 进行编排，以及 **AgentScope**
（`ReActAgent`、`AgentScopeAgent`、`Model`、`Knowledge`、`@Tool`）进行 agent 执行。

---

## 目录

- [架构概览](#架构概览)
- [RAG Agent (ragagent/)](#rag-agent-ragagent)
  - [图拓扑](#rag-图拓扑)
  - [节点详情](#rag-节点详情)
  - [状态流转](#rag-状态流转)
- [SQL Agent (sqlagent/)](#sql-agent-sqlagent)
  - [图拓扑](#sql-图拓扑)
  - [节点详情](#sql-节点详情)
  - [状态流转](#sql-状态流转)
- [关键设计模式](#关键设计模式)
  - [确定性节点 vs Agentic 节点](#确定性节点-vs-agentic-节点)
  - [合成工具调用](#合成工具调用)
  - [消息桥接：AgentScope ↔ Spring AI](#消息桥接agentscope--spring-ai)
- [项目结构](#项目结构)
- [配置](#配置)
- [构建与运行](#构建与运行)
- [如何创建自己的工作流](#如何创建自己的工作流)

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                      StateGraph Runtime                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────┐  │
│  │ Node 1   │ →  │ Node 2   │ →  │ Node 3       │ →  │ Node N   │  │
│  │ (det.)   │    │ (LLM)    │    │ (ReActAgent) │    │ (end)    │  │
│  └──────────┘    └──────────┘    └──────────────┘    └──────────┘  │
│         │              │               │                            │
│         ▼              ▼               ▼                            │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                  OverAllState (共享上下文)                      │   │
│  │  question │ rewritten_query │ documents │ messages │ input   │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

每个工作流是一个有向图，节点可以是：
- **确定性节点**：纯逻辑 —— Java 代码、数据库查询、向量搜索（无 LLM）
- **LLM 节点**：单次 LLM 调用，强制使用工具
- **Agentic 节点**：完整的 ReActAgent，可以推理并在循环中调用工具

所有节点共享一个共同的 **OverAllState** —— 可配置 `KeyStrategy` 的键值存储
（`ReplaceStrategy` 用于覆盖，`AppendStrategy` 用于累积）。

---

## RAG Agent (ragagent/)

一个检索增强生成工作流，用于回答关于 WNBA 数据（球队名单、比赛结果、球员统计）的问题。

### RAG 图拓扑

```
START ──→ [rewrite] ──→ [retrieve] ──→ [prepare_agent] ──→ [rag_agent] ──→ END
  │           │              │                 │                  │
  │     LLM 重写查询    向量搜索          格式化提示词        ReActAgent 配备
  │     以优化检索     (无 LLM)         上下文 + 问题        get_latest_news 工具
```

### RAG 节点详情

| 节点 | 类型 | 类 | 用途 |
|------|------|-------|---------|
| `rewrite` | 确定性 LLM | `RewriteNode` | 使用 `qwen-plus` 重写查询以优化检索（聚焦实体名称、球队、统计数据）。写入 `rewritten_query`。 |
| `retrieve` | 确定性 | `RetrieveNode` | 通过 `DashScopeTextEmbedding` 嵌入重写后的查询，并搜索内存向量存储。写入 `documents`。 |
| `prepare_agent` | 确定性 | `PrepareAgentNode` | 将检索到的文档拼接成上下文字符串，使用 `RagAgentConfig.buildAgentPrompt()` 构建最终提示词。同时写入 `messages`（Spring AI 格式）和 `input`（用于 AgentScopeAgent）。 |
| `rag_agent` | Agentic | `AgentScopeAgent` 包装 `ReActAgent` | 完整的 ReAct agent，配备 `get_latest_news` 工具。基于上下文 + 问题回答。 |

### RAG 状态流转

```
状态键                写入者              读取者                策略
─────────────────────────────────────────────────────────────────────────
question              服务输入            rewrite, prepare     ReplaceStrategy
rewritten_query       rewrite            retrieve             ReplaceStrategy
documents             retrieve           prepare_agent        ReplaceStrategy
messages              prepare_agent      —                    AppendStrategy
input                 prepare_agent      rag_agent            ReplaceStrategy
```

### 知识库

RAG agent 使用预填充了 9 个 WNBA 文档的内存知识库：
- **球员名单**：New York Liberty、Las Vegas Aces、Indiana Fever（3 个文档）
- **比赛**：2024 总决赛、两场常规赛（3 个文档）
- **统计数据**：A'ja Wilson、Caitlin Clark、Breanna Stewart（3 个文档）

文档使用 `text-embedding-v3`（1024 维）嵌入，存储在支持 ANN 搜索的 `InMemoryStore` 中。

---

## SQL Agent (sqlagent/)

一个通过发现表、检查模式并生成 SQL 查询来回答关于类似 Chinook 的音乐数据库问题的 agent。

### SQL 图拓扑

```
START ──→ [list_tables] ──→ [call_get_schema] ──→ [get_schema] ──→ [generate_query] ──→ END
  │             │                   │                    │                   │
  │       合成工具调用          LLM 决定           执行工具调用         ReActAgent 配备
  │       列出所有表           检查哪些表          返回模式            sql_db_query
                                                                      生成 SQL
```

### SQL 节点详情

| 节点 | 类型 | 类 | 用途 |
|------|------|-------|---------|
| `list_tables` | 确定性 | `ListTablesNode` | 为 `sql_db_list_tables` 创建**合成工具调用**，直接执行。不涉及 LLM。将工具调用 + 响应 + 摘要追加到消息中。 |
| `call_get_schema` | LLM 节点 | `CallGetSchemaNode` | 构建一个仅配备 `sql_db_schema` 工具的 ReActAgent。LLM 根据问题决定检查哪些表。将 AgentScope 工具调用转换为 Spring AI 格式。 |
| `get_schema` | 确定性 | `ExecuteGetSchemaNode` | 从最后一条消息中读取工具调用，为每个请求的表执行 `sql_db_schema`，追加结果 + 摘要。 |
| `generate_query` | Agentic | `AgentScopeAgent` 包装 `ReActAgent` | 拥有所有三个 SQL 工具。生成并执行 SELECT 查询。使用 `includeContents=true` 将累积消息作为上下文传递。 |

### SQL 状态流转

```
状态键          写入者                      读取者                    策略
─────────────────────────────────────────────────────────────────────────────
messages        list_tables,                call_get_schema,          AppendStrategy
                call_get_schema,            get_schema,
                get_schema,                 generate_query
                generate_query
llm_response    call_get_schema             —                         ReplaceStrategy
question        服务输入                    call_get_schema           ReplaceStrategy
```

### SQL 工具

| 工具 | 描述 | 安全性 |
|------|-------------|----------|
| `sql_db_list_tables` | 列出 H2 PUBLIC schema 中的所有基表 | 只读 |
| `sql_db_schema` | 返回 CREATE TABLE 列 + 3 条样本行 | 只读 |
| `sql_db_query` | 执行 SELECT 查询 | **阻止 INSERT/UPDATE/DELETE/DROP** |

数据库是 H2 内存数据库，使用类似 Chinook 的模式（启动时从 `schema-chinook.sql` 初始化）。

---

## 关键设计模式

### 确定性节点 vs Agentic 节点

两个工作流中的一个关键设计决策是选择哪些节点应涉及 LLM，哪些应是纯代码：

```java
// ❌ 不好：使用 LLM 列出数据库表（浪费）
// ✅ 好：直接 JDBC 查询（ListTablesNode）

// ❌ 不好：硬编码要检查哪些表
// ✅ 好：让 LLM 根据问题决定（CallGetSchemaNode）
```

**经验法则：** 如果一项任务可以通过数据库查询、向量搜索或字符串格式化完成 —— 保持确定性。
仅在需要真正推理或自然语言理解的任务时使用 LLM 调用。

### 合成工具调用

在 `ListTablesNode` 中，我们创建了一个**合成工具调用** —— 伪造一个看起来像是来自 LLM 的
`AssistantMessage.ToolCall`，但实际上由代码生成：

```java
// 这创建了一个看起来像是 LLM 决定调用它的工具调用
AssistantMessage.ToolCall toolCall =
    new AssistantMessage.ToolCall(callId, "function", "sql_db_list_tables", "{}");
```

**为什么？** 这保持了整个图中消息格式的一致性 —— 无论工具是由 LLM 还是由确定性代码调用，
下游节点都会看到相同的消息结构。这种一致性简化了节点的实现。

### 消息桥接：AgentScope ↔ Spring AI

AgentScope 和 Spring AI 都有自己的消息表示。由于 `StateGraph` 使用 Spring AI 消息，
但 AgentScope agent 使用 `Msg` 对象，我们需要一个桥接：

- **AgentScope → Spring AI**：`CallGetSchemaNode.toAssistantMessage()` 将
  `Msg` + `ToolUseBlock` 转换为带 `ToolCall` 的 `AssistantMessage`
- **Spring AI → AgentScope**：`CallGetSchemaNode.buildUserText()` 从
  Spring AI 消息中提取文本来构建 AgentScope 用户提示词

在混合 AgentScope agent 和 Spring AI 的 StateGraph 时，这种桥接模式是必不可少的。

---

## 项目结构

```
agentscope-examples/multiagent-patterns/workflow/
├── README.md                              # 本文件
├── pom.xml                                # Maven POM（Spring Boot + AgentScope）
└── src/main/
    ├── java/com/alibaba/cloud/ai/examples/multiagents/workflow/
    │   ├── WorkflowApplication.java       # Spring Boot 入口点
    │   │
    │   ├── ragagent/                      # RAG agent 工作流
    │   │   ├── RagAgentConfig.java        # @Configuration：beans、图、提示词
    │   │   ├── RagAgentService.java       # 服务：调用图、提取结果
    │   │   ├── RagAgentRunner.java        # @Component：启动时一次性演示
    │   │   ├── node/
    │   │   │   ├── RewriteNode.java       # LLM 查询重写器
    │   │   │   ├── RetrieveNode.java      # 向量相似度搜索
    │   │   │   └── PrepareAgentNode.java  # 提示词格式化器
    │   │   └── tools/
    │   │       └── RagAgentTools.java     # @Tool：get_latest_news
    │   │
    │   └── sqlagent/                      # SQL agent 工作流
    │       ├── SqlAgentConfig.java        # @Configuration：beans、图、提示词
    │       ├── SqlAgentService.java       # 服务：调用图、提取结果
    │       ├── SqlAgentRunner.java        # @Component：启动时一次性演示
    │       ├── node/
    │       │   ├── ListTablesNode.java    # 合成工具调用，列出表
    │       │   ├── CallGetSchemaNode.java # LLM 决定检查哪些表
    │       │   └── ExecuteGetSchemaNode.java # 执行模式工具，返回结果
    │       └── tools/
    │           └── SqlTools.java          # @Tool：list_tables、schema、query
    │
    └── resources/
        ├── application.yml                # Spring Boot 配置
        └── schema-chinook.sql             # H2 模式（类似 Chinook）
```

---

## 配置

| 属性 | 默认值 | 描述 |
|----------|---------|-------------|
| `workflow.rag.enabled` | `false` | 启用 RAG agent beans |
| `workflow.sql.enabled` | `true` | 启用 SQL agent beans |
| `workflow.runner.enabled` | `true` | 启动时运行一次性演示 |
| `spring.ai.dashscope.api-key` | — | DashScope API 密钥（或 `AI_DASHSCOPE_API_KEY` 环境变量） |

启动时 runner 一次只能启用一个工作流（它们共享同一个聊天模型 bean 名称）。

---

## 构建与运行

### 前置条件

- JDK 17+
- Maven 3.6+
- DashScope API 密钥：`export AI_DASHSCOPE_API_KEY=your-key`

### 构建

```bash
# 从仓库根目录
./mvnw -pl agentscope-examples/multiagent-patterns/workflow -am -B package -DskipTests
```

### 运行：RAG Agent

```bash
./mvnw -pl agentscope-examples/multiagent-patterns/workflow spring-boot:run \
  -Dspring-boot.run.arguments="--workflow.rag.enabled=true --workflow.runner.enabled=true"
```

runner 会问：*"Who won the 2024 WNBA Championship?"*

### 运行：SQL Agent

```bash
./mvnw -pl agentscope-examples/multiagent-patterns/workflow spring-boot:run \
  -Dspring-boot.run.arguments="--workflow.sql.enabled=true --workflow.runner.enabled=true"
```

runner 会问：*"Which genre on average has the longest tracks?"*

---

## 如何创建自己的工作流

1. **定义图拓扑**：将处理阶段列为节点。确定哪些是确定性节点、LLM 节点还是 agentic 节点。

2. **定义状态键**：节点之间流动哪些数据？选择 `ReplaceStrategy`（覆盖）或 `AppendStrategy`（累积）。

3. **实现节点**：每个节点实现 `NodeAction.apply(OverAllState)`。从状态读取、处理、写回。

4. **对于 agentic 节点**：使用 `AgentScopeAgent.fromBuilder(ReActAgent.builder()...)`
   并调用 `.asNode()` 获取与 StateGraph 兼容的 `NodeAction`。

5. **连接图**：使用 `stateGraph.addEdge(START, "node1").addEdge("node1", "node2")...`

6. **编译并调用**：调用 `stateGraph.compile()` 获取 `CompiledGraph`，然后 `graph.invoke(inputs)`。

7. **提取结果**：调用后，扫描 `state.value("messages")` 获取最后一个 `AssistantMessage`。

参见 `RagAgentConfig` 和 `SqlAgentConfig` 获取完整的可运行示例。

---

## 相关文档

- [自定义工作流模式](../../../docs/en/multi-agent/workflow.md) —— 框架文档
- [RAG Agent 详情](src/main/java/com/alibaba/cloud/ai/examples/multiagents/workflow/ragagent/) —— 源代码
- [SQL Agent 详情](src/main/java/com/alibaba/cloud/ai/examples/multiagents/workflow/sqlagent/) —— 源代码
