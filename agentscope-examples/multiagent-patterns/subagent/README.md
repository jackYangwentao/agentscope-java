# Subagent 模式 - 技术尽职调查助手

一个多 agent 示例，演示 **TaskTool** 模式：主编排 agent 将复杂工作委托给专门的子 agent。

## 概述

**技术尽职调查助手**通过以下方式结合来评估软件项目：

- **代码库分析**：结构、依赖、模式、技术债务
- **网络调研**：文档、替代方案、基准测试、生态系统

主 agent 使用 `write_todos` 进行规划，并通过 **Task** 和 **TaskOutput** 工具将任务委托给子 agent。

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                  技术尽职调查助手                                 │
│  （编排器：write_todos, Task, TaskOutput, glob, grep, web）       │
└────────────────────────────┬────────────────────────────────────┘
                              │ 通过 Task 工具委托
    ┌─────────────────────────┼─────────────────────────┬──────────────────┐
    ▼                         ▼                         ▼                  ▼
┌──────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│codebase-     │  │ web-researcher  │  │ general-purpose │  │ dependency-analyzer  │
│explorer      │  │ web_fetch       │  │ glob, grep, web │  │ （API 定义）          │
│glob, grep    │  │                 │  │                 │  │ glob, grep           │
│（Markdown）   │  │ （Markdown）     │  │ （Markdown）     │  └─────────────────────┘
└──────────────┘  └─────────────────┘  └─────────────────┘
```

## 子 Agent

子 agent 可以通过两种方式定义：

### 1. Markdown（基于文件）

| Agent | 工具 | 用途 |
|-------|------|------|
| **codebase-explorer** | glob_search, grep_search | 查找文件、搜索代码、分析结构 |
| **web-researcher** | web_fetch | 抓取 URL、调研文档、比较技术 |
| **general-purpose** | glob_search, grep_search, web_fetch | 代码 + 网页综合分析 |

定义在 `src/main/resources/agents/*.md` 中，使用 YAML front matter。

### 2. API（编程方式）

| Agent | 工具 | 用途 |
|-------|------|------|
| **dependency-analyzer** | glob_search, grep_search | 分析依赖、版本冲突、过时的库 |

通过 Java 中的 AgentScope `ReActAgent` 和 `AgentScopeAgent` 定义，使用 `TaskToolsBuilder.subAgent()` 和编排器图注册。

## 运行

### 前提条件

- JDK 17+
- 已设置 `AI_DASHSCOPE_API_KEY` 环境变量

### 交互模式

```bash
# 在项目根目录下运行交互式聊天
AI_DASHSCOPE_API_KEY=your_key ./mvnw -pl agentscope-examples/multiagent-patterns/subagent spring-boot:run \
  -Dspring-boot.run.arguments="--subagent.run-interactive=true"
```

或在 `application.yml` 中设置：

```yaml
subagent:
  run-interactive: true
```

### 示例提示词

- **简单**："查找此项目中所有 Java 文件"
- **代码库**："该项目使用了哪些框架和依赖？"
- **网页**："抓取 https://spring.io/projects/spring-ai 并总结其功能"
- **依赖（API 子 agent）**："分析此项目的依赖是否存在版本冲突和过时的库"
- **组合**："分析此代码库中 Spring 的使用情况，然后研究 Spring AI 的替代方案并与我们当前的配置进行比较"

### 编程方式使用

编排器和 dependency-analyzer 是 **AgentScopeAgent** bean；图调用编排器。使用 `OrchestratorService` 运行完整流程：

```java
@Autowired
OrchestratorService orchestratorService;

String answer = orchestratorService.run(
    "分析此代码库的技术债务并研究 Spring AI 文档");
```

## 配置

| 属性 | 默认值 | 描述 |
|------|--------|------|
| `subagent.workspace-path` | `${user.dir}` | glob_search 和 grep_search 的根路径 |
| `subagent.run-interactive` | `false` | 启动时运行交互式聊天 |

## 关键组件

- **TaskToolsBuilder**：构建 Task + TaskOutput 工具。支持两种方式：
  - **Markdown**：`addAgentResource()` / `addAgentDirectory()` 从 `.md` 文件加载 spec
  - **API**：`subAgent(type, ReactAgent)` 注册编程方式定义的 ReActAgent
- **TodoListInterceptor**：注入 write_todos 工具和任务规划系统提示词
- **Agent spec（Markdown）**：YAML front matter 中的 `name`、`description`、`tools`（逗号分隔）

## 相关

- [subagents.md](../../../multiagents/subagents.md) - Subagent 架构文档
- [spring-ai-agent-utils subagent-demo](../../../multiagents/spring-ai-agent-utils/examples/subagent-demo) - 使用 Spring AI 社区工具的类似模式
