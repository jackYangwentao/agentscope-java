# AgentSkillExample 执行流程分析

> 文件位置：`agentscope-examples/documentation/quickstart/src/main/java/io/agentscope/examples/quickstart/AgentSkillExample.java`
>
> 核心依赖：`ReActAgent`、`SkillBox`、`AgentSkill`、`FileSystemSkillRepository`、`ShellCommandTool`
>
> 本文档追踪 Agent 技能（Skill）的**加载→注册→激活→系统提示词注入→工具化**完整执行链路。

---

## 目录

1. [示例源码](#1-示例源码)
2. [构造阶段 — 技能与工具的初始化](#2-构造阶段--技能与工具的初始化)
3. [构造阶段 — Agent 构建](#3-构造阶段--agent-构建)
4. [运行时执行流程](#4-运行时执行流程)
5. [Skill 集成深度解析](#5-skill-集成深度解析)
   - [5.1 SkillBox 注册流程](#51-skillbox-注册流程)
   - [5.2 build() 中 configureSkillBox 流程](#52-build-中-configureskillbox-流程)
   - [5.3 PreCallEvent 中 SkillHook 注入](#53-precallevent-中-skillhook-注入)
   - [5.4 codeExecution 注册流程](#54-codeexecution-注册流程)
   - [5.5 load_skill_through_path 工具调用链路](#55-load_skill_through_path-工具调用链路)
6. [完整调用链](#6-完整调用链)
7. [关键设计要点](#7-关键设计要点)

---

## 1. 示例源码

```java
public class AgentSkillExample {
    private static final String SKILL_NAME = "skill-creator";
    private static final String RESOURCES_DIR =
            "agentscope-examples/quickstart/src/main/resources/skills";
    private static final String OUTPUT_DIR =
            "agentscope-examples/quickstart/target/skill-output";

    public static void main(String[] args) throws Exception {
        // (1) 创建 Toolkit
        Toolkit toolkit = new Toolkit();

        // (2) 创建 SkillBox（绑定 toolkit）
        SkillBox skillBox = new SkillBox(toolkit);

        // (3) 从文件系统加载 skill-creator 技能
        AgentSkill skillCreator = loadSkillCreatorSkill();
        skillBox.registration().skill(skillCreator).apply();     // 注册技能

        // (4) 配置代码执行环境（shell + read + write）
        Path outputDir = resolvePath(OUTPUT_DIR);
        Scanner scanner = new Scanner(System.in);
        ShellCommandTool shellCommandTool = new ShellCommandTool(
                Set.of("python", "ls", "cat"),
                cmd -> { /* 用户审批回调 */ });

        skillBox.codeExecution()
                .workDir(outputDir.toString())
                .withShell(shellCommandTool)                    // 自定义 shell
                .withRead()                                     // 启用读文件
                .withWrite()                                    // 启用写文件
                .enable();

        // (5) 构建 Agent
        ReActAgent agent = ReActAgent.builder()
                .name("SkillCreator")
                .sysPrompt(buildSystemPrompt(outputDir))
                .model(OllamaChatModel.builder()...build())
                .toolkit(toolkit)
                .skillBox(skillBox)      // ★ 传入 SkillBox
                .memory(new InMemoryMemory())
                .build();

        // (6) 启动交互式对话
        ExampleUtils.startChat(agent);
    }
}
```

---

## 2. 构造阶段 — 技能与工具的初始化

### 2.1 `Toolkit` 创建（空）

```java
Toolkit toolkit = new Toolkit();
```

创建一个空的 Toolkit（无注册工具），后续所有工具都会通过 SkillBox 链路注册进来。

### 2.2 `SkillBox` 创建（绑定 toolkit）

```
SkillBox(toolkit)
  │
  ├─ this.skillRegistry = new SkillRegistry()              ← 技能注册中心
  ├─ this.skillPromptProvider = new AgentSkillPromptProvider(registry)
  │     └─ 负责生成技能目录的 XML 系统提示词
  ├─ this.skillToolFactory = new SkillToolFactory(registry, toolkit)
  │     └─ 负责创建"load_skill_through_path"工具
  └─ this.toolkit = toolkit                                ← 保存引用
```

### 2.3 技能从文件系统加载

```
loadSkillCreatorSkill()
  │
  └─ FileSystemSkillRepository(resourcesDir, false)        ← read-only 仓库
       │
       ├─ 参数校验：目录是否存在、是否为目录
       └─ getSkill("skill-creator")
            │
            └─ SkillFileSystemHelper.loadSkill(baseDir, "skill-creator", source)
                 │
                 ├─ 读取 <baseDir>/skill-creator/SKILL.md
                 ├─ 解析 YAML 前置元数据（name, description, 等）
                 ├─ 提取 skillContent（SKILL.md 元数据之后的主体内容）
                 ├─ 收集 resources/ 目录下的所有资源文件
                 └─ 返回 AgentSkill 对象
```

### 2.4 技能注册到 SkillBox

```
skillBox.registration().skill(skillCreator).apply()
  │
  ├─ skillBox.registerSkill(skill)                         ← 存入 skillRegistry
  │    └─ skillRegistry.registerSkill(skillId, skill, registered)
  │         ├─ skillMap.put(skillId, skill)                  ← 技能本体
  │         └─ registeredSkills.put(skillId, registered)      ← 注册状态（含激活标志）
  │
  └─ 无 tool/agentTool/mcpClient/subAgent → 跳过工具注册     ← 本例只注册技能不绑定工具
```

### 2.5 代码执行环境配置

```
skillBox.codeExecution()
    .workDir(outputDir)              ← "/path/to/target/skill-output"
    .withShell(shellCommandTool)     ← 自定义 ShellCommandTool
    .withRead()                      ← 启用读文件工具
    .withWrite()                     ← 启用写文件工具
    .enable()                        ★ 关键：应用配置
  │
  └─ enable() [SkillBox.java:1182-1244]
       │
       ├─ [1] 创建工作目录（如不存在）
       ├─ [2] 设置文件上传目录（workDir/skills）
       │
       ├─ [3] Shell Tool
       │    └─ cloneShellToolWithWorkDir(shellCommandTool, workDir)
       │         ├─ 复制 allowedCommands、approvalCallback、commandValidator
       │         └─ 更新 baseDir = workDir
       │    └─ toolkit.registration()
       │         .agentTool(shellTool)
       │         .group("skill_code_execution_tool_group")
       │         .apply()                                   ★ 注册到 toolkit
       │
       ├─ [4] Read Tool
       │    └─ ReadFileTool readTool = new ReadFileTool(workDir)
       │    └─ toolkit.registration()
       │         .tool(readTool)
       │         .group("skill_code_execution_tool_group")
       │         .apply()                                   ★ 注册到 toolkit
       │
       ├─ [5] Write Tool
       │    └─ WriteFileTool writeTool = new WriteFileTool(workDir)
       │    └─ toolkit.registration()
       │         .tool(writeTool)
       │         .group("skill_code_execution_tool_group")
       │         .apply()                                   ★ 注册到 toolkit
       │
       └─ [6] skillBox.skillPromptProvider
                .setCodeExecutionEnable(true)               ★ 标记：提示词中追加
                .setCodeExecutionInstruction(instruction)     代码执行指令
```

时间点 2.5 完成后，Toolkit 中的状态：

| 工具组 | 工具 | 描述 |
|---|---|---|
| `skill_code_execution_tool_group` | `execute_command` | ShellCommandTool（python/ls/cat + 审批回调） |
| `skill_code_execution_tool_group` | `read_text_file` | ReadFileTool |
| `skill_code_execution_tool_group` | `write_text_file` | WriteFileTool |

---

## 3. 构造阶段 — Agent 构建

### 3.1 `ReActAgent.builder().skillBox(skillBox)` 内部

```java
// ReActAgent.java Builder.build() 流程 (line 1674)
build()
  │
  ├─ Toolkit agentToolkit = this.toolkit.copy()              ★ 深拷贝 toolkit
  │
  ├─ registerToolsFromHooks(agentToolkit)                    ← 无 hook 工具
  │
  ├─ if (skillBox != null) configureSkillBox(agentToolkit)   ★ 核心：配置技能
  │    │
  │    └─ configureSkillBox() [ReActAgent.java:1891-1902]
  │         │
  │         ├─ skillBox.bindToolkit(agentToolkit)
  │         │    ├─ this.toolkit = agentToolkit              ← 重新绑定到拷贝后的 toolkit
  │         │    └─ skillToolFactory.bindToolkit(agentToolkit)
  │         │
  │         ├─ skillBox.registerSkillLoadTool()
  │         │    ├─ 创建工具组 "skill-build-in-tools"        ← 如果不存在
  │         │    └─ toolkit.registration()
  │         │         .agentTool(skillToolFactory.createSkillAccessToolAgentTool())
  │         │         .group("skill-build-in-tools")
  │         │         .apply()                               ★ 注册 load_skill_through_path
  │         │
  │         ├─ if (skillBox.isAutoUploadSkill())
  │         │    skillBox.uploadSkillFiles()                 ← 上传技能文件到 uploadDir
  │         │
  │         └─ hooks.add(new SkillHook(skillBox))            ★ 添加技能提示注入钩子
  │
  ├─ return new ReActAgent(this, agentToolkit)
  │    │
  │    ├─ super(name, desc, checkRunning, hooks, agentToolkit, ...)
  │    │    └─ this.toolkit = agentToolkit                   ← 存入 StructuredOutputCapableAgent
  │    │
  │    └─ this.memory = builder.memory                       ← 存入 ReActAgent
  │
  └─ Agent 实例
       ├─ toolkit (深拷贝，已包含 load_skill_through_path + 代码执行工具)
       ├─ memory = InMemoryMemory
       └─ hooks = [SkillHook(priority=85), ...]
```

### 3.2 build() 完成后的架构

```
┌─────────────────────────────────────────────────────┐
│                    ReActAgent                         │
│                                                       │
│  ┌──────────────────────┐  ┌──────────────────────┐  │
│  │      Toolkit          │  │       Memory          │  │
│  │  (深拷贝副本)          │  │  (InMemoryMemory)     │  │
│  │                       │  │                       │  │
│  │ skill-build-in-tools  │  │  [用户消息]           │  │
│  │  ├─ load_skill_through│  │  [模型回复]           │  │
│  │  │  _path (激活)      │  │  [工具结果]           │  │
│  │                       │  │                       │  │
│  │ skill_code_execution_ │  │                       │  │
│  │ tool_group (激活)     │  │                       │  │
│  │  ├─ execute_command   │  │                       │  │
│  │  ├─ read_text_file    │  │                       │  │
│  │  └─ write_text_file   │  │                       │  │
│  └──────────────────────┘  └──────────────────────┘  │
│                                                       │
│  ┌──────────────────────────────────────────────┐     │
│  │               SkillBox                         │     │
│  │                                                │     │
│  │  AgentSkill[skill-creator]                     │     │
│  │    ├─ 名称: skill-creator                      │     │
│  │    ├─ SKILL.md 内容 (技能指令)                  │     │
│  │    ├─ resources/ 文件                          │     │
│  │    └─ 激活状态: false (默认)                    │     │
│  │                                                │     │
│  │  AgentSkillPromptProvider                      │     │
│  │    └─ getSkillSystemPrompt() → XML 格式的技能目录│     │
│  └──────────────────────────────────────────────┘     │
│                                                       │
│  Hook链:                                              │
│    [SkillHook(priority=85)]  → 注入技能系统提示词     │
└─────────────────────────────────────────────────────┘
```

---

## 4. 运行时执行流程

### 4.1 入口：`ExampleUtils.startChat(agent)`

```
用户输入 "帮我创建一个叫 greetings 的新技能"
  │
  └─ agent.stream(userMsg, streamOptions)
       │
       └─ [AgentBase.call]
            │
            ├─ beforeAgentExecution(msgs)        ← 初始化 RuntimeContext
            │
            ├─ notifyPreCall(msgs)
            │    │
            │    ├─ [PreCallEvent] 生成 systemMsg
            │    │
            │    └─ [SkillHook.onEvent]           ← ★ 技能提示注入
            │         │
            │         ├─ skillBox.getSkillPrompt()
            │         │    └─ skillPromptProvider.getSkillSystemPrompt()
            │         │         └─ 遍历 skillRegistry 中所有技能
            │         │              渲染为 XML 格式：
            │         │              <skills>
            │         │                <skill>
            │         │                  <name>skill-creator</name>
            │         │                  <description>...</description>
            │         │                  <skill-id>skill-creator</skill-id>
            │         │                </skill>
            │         │              </skills>
            │         │             + 代码执行指令（说明可用工具和输出目录）
            │         │
            │         └─ preCallEvent.appendSystemContent(skillPrompt)
            │              技能提示词追加到系统消息尾部
            │
            ├─ doCall(msgs)                      ← [ReActAgent 核心循环]
            │    │
            │    ├─ addToMemory(msgs)             ★ memory: 存用户消息
            │    │
            │    └─ executeIteration(0)
            │         │
            │         └─ reasoning(0, false)
            │              │
            │              ├─ model.stream(modelInput, toolkit.getToolSchemas(), options)
            │              │    │                     ★ toolkit: 传入工具 Schema
            │              │    │
            │              │    │ 模型输入 (简化)：
            │              │    │  [System]
            │              │    │    你是一个有帮助的 AI 助手...
            │              │    │    <!-- 技能提示注入 -->
            │              │    │    <skills>
            │              │    │      <skill>
            │              │    │        <name>skill-creator</name>
            │              │    │        <description>用于创建新技能</description>
            │              │    │        <skill-id>skill-creator</skill-id>
            │              │    │      </skill>
            │              │    │    </skills>
            │              │    │    <!-- 代码执行指令 -->
            │              │    │    你有以下文件工具可用：execute_command, read_text_file, write_text_file
            │              │    │    工作目录：/path/to/target/skill-output
            │              │    │
            │              │    │  [User]
            │              │    │    帮我创建一个叫 greetings 的新技能
            │              │
            │              └─ [LLM 回复]
            │                   可能包含 ToolUseBlock 序列：
            │                   ① load_skill_through_path("skill-creator/SKILL.md")
            │                      ↓ 加载 skill-creator 技能指令
            │                   ② write_text_file("greetings/SKILL.md", "...")
            │                      ↓ 在磁盘上创建新技能文件
            │                   ③ execute_command("python validate.py")
            │                      ↓ 执行验证脚本
```

### 4.2 技能加载流程（LLM 调用 load_skill_through_path 时）

```
LLM 调用 load_skill_through_path(path="skill-creator/SKILL.md")
  │
  └─ SkillToolFactory.createSkillAccessToolAgentTool()
       └─ 工具执行
            │
            ├─ 从 toolkit 反向查找 SkillBox
            │    └─ skillBox.getSkill(skillId)  ← "skill-creator"
            │
            ├─ activateSkill(skillId)            ★ 激活技能
            │    ├─ registeredSkill.setActive(true)
            │    └─ syncToolGroupStates()
            │         └─ toolkit.updateToolGroups(skillToolGroup, true)
            │              启用技能关联的工具组
            │
            ├─ 返回 SKILL.md 内容给 LLM
            │
            └─ LLM 获得技能指令后，使用代码执行工具实现
                 ├─ write_text_file(greetings/SKILL.md, ...)
                 ├─ execute_command(python validate.py)
                 └─ ...
```

---

## 5. Skill 集成深度解析

### 5.1 SkillBox 注册流程

```
skillBox.registration().skill(skill).apply()
  │
  ├─ skillBox.registerSkill(skill)
  │    ├─ RegisteredSkill registered = new RegisteredSkill(skillId)
  │    │    └─ isActive = false (默认未激活)
  │    └─ skillRegistry.registerSkill(skillId, skill, registered)
  │         ├─ skillMap.put(skillId, skill)            ← 技能内容
  │         └─ registeredSkills.put(skillId, registered) ← 注册元数据
  │
  └─ [无 tool/agentTool → 跳过工具组创建]
```

### 5.2 build() 中 configureSkillBox 流程

```
configureSkillBox(agentToolkit)        [ReActAgent.java:1891]
  │
  ├─ bindToolkit(agentToolkit)         ← 将 SkillBox 绑定到深拷贝后的 toolkit
  │
  ├─ registerSkillLoadTool()
  │    ├─ toolkit.createToolGroup("skill-build-in-tools")  ← 创建内置工具组
  │    └─ toolkit.registration()
  │         .agentTool(skillAccessTool)
  │         .group("skill-build-in-tools")
  │         .apply()
  │
  ├─ uploadSkillFiles()                ← 同步技能资源文件到 workDir/skills/
  │
  └─ hooks.add(new SkillHook(skillBox))  ← 添加到 Hook 链
```

### 5.3 PreCallEvent 中 SkillHook 注入

```
每次 call/stream 触发 PreCallEvent
  │
  └─ SkillHook.onEvent(event)           [priority=85]
       │
       ├─ skillBox.getSkillPrompt()
       │    └─ AgentSkillPromptProvider.getSkillSystemPrompt()
       │         │
       │         ├─ 从 skillRegistry.skillMap 获取已注册技能
       │         │
       │         ├─ 遍历技能 → 渲染 XML
       │         │    <skills>
       │         │      <skill>
       │         │        <name>skill-creator</name>
       │         │        <description>创建新技能的技能</description>
       │         │        <skill-id>skill-creator</skill-id>
       │         │        <source>filesystem-...</source>
       │         │      </skill>
       │         │    </skills>
       │         │
       │         ├─ 若代码执行启用 → 追加代码执行指令
       │         │    工作目录：/path/to/output
       │         │    可用工具：execute_command, read_text_file, write_text_file
       │         │
       │         └─ 返回完整技能提示字符串
       │
       └─ preCallEvent.appendSystemContent(skillPrompt)
            技能提示追加到系统消息中，对 LLM 可见
```

### 5.4 codeExecution 注册流程

```
skillBox.codeExecution()
    .workDir(outputDir)
    .withShell(customShellTool)
    .withRead()
    .withWrite()
    .enable()
  │
  └─ enable() [SkillBox.java]
       │
       ├─ [Shell] cloneShellToolWithWorkDir(customShell, workDir)
       │    → agentTool(ShellCommandTool) 注册到 group="skill_code_execution_tool_group"
       │
       ├─ [Read] new ReadFileTool(workDir)
       │    → tool(ReadFileTool) 注册到 group="skill_code_execution_tool_group"
       │
       ├─ [Write] new WriteFileTool(workDir)
       │    → tool(WriteFileTool) 注册到 group="skill_code_execution_tool_group"
       │
       └─ skillPromptProvider.setCodeExecutionEnable(true)
            → 技能提示词末尾追加代码执行指令
```

### 5.5 load_skill_through_path 工具调用链路

```
LLM 决定调用 load_skill_through_path
  │
  ├─ [ReActAgent.acting()]
  │    └─ toolkit.callTools(toolCalls, ...)
  │         └─ ToolExecutor.executeAll()
  │              └─ AgentTool.callAsync(ToolCallParam)
  │                   │
  │                   └─ SkillToolFactory 创建的 AgentTool
  │                        ├─ 解析参数：path = "skill-creator/SKILL.md"
  │                        │
  │                        ├─ skillBox.getSkill("skill-creator")
  │                        │    └─ skillRegistry.getSkill(skillId)
  │                        │
  │                        ├─ 如果是 SKILL.md → 返回技能内容
  │                        │
  │                        ├─ 如果是 resources/xxx → 返回资源内容
  │                        │
  │                        ├─ registerSkill.setActive(true)      ★ 激活技能
  │                        └─ skillBox.syncToolGroupStates()     ★ 同步工具组
  │
  └─ [结果写回 memory]
       └─ memory.addMessage(toolResultMsg)
```

---

## 6. 完整调用链

```
AgentSkillExample.main()
│
├─ [初始化: Toolkit + SkillBox]
│   └─ new SkillBox(toolkit)
│        ├─ new SkillRegistry()
│        ├─ new AgentSkillPromptProvider(registry)
│        └─ new SkillToolFactory(registry, toolkit)
│
├─ [加载技能: 文件系统 → AgentSkill]
│   ├─ new FileSystemSkillRepository(resourcesDir, false)
│   │   └─ 校验目录存在性
│   └─ repository.getSkill("skill-creator")
│       └─ SkillFileSystemHelper.loadSkill(baseDir, name, source)
│           ├─ 读取 SKILL.md
│           ├─ MarkdownSkillParser 解析 YAML frontmatter
│           ├─ 提取 skillContent + resources
│           └─ 返回 AgentSkill 对象
│
├─ [注册技能: SkillBox]
│   └─ skillBox.registration().skill(skillCreator).apply()
│       └─ skillBox.registerSkill(skill)
│           └─ skillRegistry.registerSkill(id, skill, registered)
│
├─ [代码执行: SkillBox.codeExecution().enable()]
│   └─ enable()
│       ├─ toolkit.registration().agentTool(shellTool).group("skill_code_execution_tool_group").apply()
│       ├─ toolkit.registration().tool(readFileTool).group("skill_code_execution_tool_group").apply()
│       ├─ toolkit.registration().tool(writeFileTool).group("skill_code_execution_tool_group").apply()
│       └─ skillPromptProvider.setCodeExecutionEnable(true)
│
├─ [构建 Agent: ReActAgent.builder().skillBox(skillBox).build()]
│   └─ build()
│       ├─ Toolkit agentToolkit = toolkit.copy()           ← 深拷贝
│       │
│       └─ configureSkillBox(agentToolkit)                 ★ 核心
│           ├─ skillBox.bindToolkit(agentToolkit)
│           ├─ skillBox.registerSkillLoadTool()
│           │   └─ toolkit.registration()
│           │        .agentTool(skillToolFactory.createSkillAccessToolAgentTool())
│           │        .group("skill-build-in-tools")
│           │        .apply()                               ← 注册 load_skill_through_path
│           │
│           ├─ skillBox.uploadSkillFiles()                 ← 上传资源文件
│           │
│           └─ hooks.add(new SkillHook(skillBox))          ← 添加技能提示钩子
│
├─ [Agent 就绪: 等待用户输入]
│
└─ ExampleUtils.startChat(agent)
    │
    └─ [交互循环]
        │
        └─ agent.stream(userMsg, streamOptions)
            │
            └─ [每轮 AgentBase.call()]
                │
                ├─ beforeAgentExecution(msgs)
                │
                ├─ [PreCallEvent] notifyPreCall(msgs)
                │   │
                │   └─ SkillHook.onEvent()                  ★ 注入技能提示
                │       └─ preCallEvent.appendSystemContent(skillPrompt)
                │
                ├─ [ReActAgent.doCall()]
                │   ├─ addToMemory(msgs)
                │   └─ reasoning(0)
                │       ├─ model.stream(memory_msg, toolkit.getToolSchemas())
                │       │                                ★ 工具 Schema 发给 LLM
                │       │                                 包含：
                │       │   - load_skill_through_path (skill-build-in-tools)
                │       │   - execute_command (skill_code_execution_tool_group)
                │       │   - read_text_file (skill_code_execution_tool_group)
                │       │   - write_text_file (skill_code_execution_tool_group)
                │       │
                │       ├─ memory.addMessage(reply)          ★ memory 写
                │       │
                │       └─ [LLM 决定调用 load_skill_through_path]
                │            └─ acting(iter)
                │                ├─ toolkit.callTools()       ★ toolkit 执行
                │                │    ├─ load_skill_through_path → 激活 skill-creator
                │                │    │                        → 返回 SKILL.md 内容
                │                │    ├─ write_text_file(...) → 创建新技能文件
                │                │    └─ execute_command(...) → 执行验证脚本
                │                │
                │                └─ memory.addMessage(toolResult)  ★ memory 写
                │
                └─ [循环] → 下一轮 reasoning/acting ... 直到完成
```

---

## 7. 关键设计要点

### 7.1 Skill 的核心集成机制

```
┌──────────────────────────────────────────────────────────────────┐
│                         Agent 每次 call()                          │
│                                                                   │
│   PreCallEvent                                                     │
│      │                                                             │
│      ├─ SkillHook.onEvent()                                       │
│      │    └─ appendSystemContent("<skills>...</skills>") ← 技能目录  │
│      │                                                             │
│      ▼                                                             │
│   [System Message] = sysPrompt + Skill Prompt + Code Exec Prompt  │
│                                                                   │
│      ▼                                                             │
│   model.stream(with toolkit.getToolSchemas())                      │
│      │                                                             │
│      │  LLM 看到：                                                  │
│      │  ① 技能目录 → 决定调用 load_skill_through_path               │
│      │  ② 代码执行工具 → 决定写文件 / 执行脚本                       │
│      │                                                             │
│      ▼                                                             │
│   acting() → toolkit.callTools()                                   │
│      ├─ load_skill_through_path → activateSkill → syncToolGroups   │
│      └─ write_text_file / execute_command → 实现技能逻辑            │
└──────────────────────────────────────────────────────────────────┘
```

**三个阶段**：
1. **注册**（构造时）：`SkillBox.registerSkill()` → `SkillRegistry`
2. **提示注入**（每次 call）：`SkillHook.onEvent()` → 技能目录 XML → 追加到系统消息
3. **动态加载**（LLM 触发）：`load_skill_through_path` → 激活技能 → 启用工具组

### 7.2 SkillBox 的 toolkit 绑定双阶段

| 阶段 | 调用 | 绑定的 toolkit | 说明 |
|---|---|---|---|
| ① 构造 | `new SkillBox(toolkit)` | 原始 toolkit | 注册技能、配置代码执行工具 |
| ② build | `skillBox.bindToolkit(agentToolkit)` | 深拷贝后的 toolkit | 重新绑定到 Agent 私有副本 |

为什么需要两阶段？
- `ReActAgent.Builder.build()` 对 toolkit 做 `copy()` 深拷贝
- copy 之后，SkillBox 仍持有原始 toolkit 引用
- `bindToolkit()` 将 SkillBox 重新指向深拷贝后的 toolkit，使技能内置工具和代码执行工具都在正确的 toolkit 实例上

### 7.3 技能激活与工具组状态同步

```
LLM 调用 load_skill_through_path("skill-creator/SKILL.md")
  │
  ├─ 返回 SKILL.md 内容给 LLM
  │
  └─ activateSkill("skill-creator")
       └─ registeredSkill.setActive(true)
       └─ skillBox.syncToolGroupStates()
            │
            ├─ 遍历所有 registeredSkills
            ├─ 若技能激活 → toolkit.updateToolGroups(toolGroup, true)   ← 启用
            └─ 若技能未激活 → toolkit.updateToolGroups(toolGroup, false) ← 停用
```

技能默认是 **未激活** 状态，只有在 LLM 调用 `load_skill_through_path` 加载后才会激活。
激活后，技能关联的工具组才变为可用，LLM 才能调用该技能中的工具。

### 7.4 ShellCommandTool 的安全机制

```java
ShellCommandTool shellTool = new ShellCommandTool(
    Set.of("python", "ls", "cat"),            // 命令白名单
    cmd -> {
        System.out.println("Enter y/n to approve or deny:");
        System.out.println(cmd);
        String response = scanner.nextLine();
        return response.equalsIgnoreCase("y"); // 用户审批回调
    });
```

| 机制 | 作用 |
|---|---|
| 命令白名单 | 仅允许 `python`、`ls`、`cat` 三个命令 |
| 审批回调 | 每执行一个命令前弹窗询问用户 y/n |
| baseDir 覆盖 | `cloneShellToolWithWorkDir` 限制到 workDir 内 |
| CommandValidator | 平台相关校验（Unix/Windows）阻止恶意命令组合 |
| 超时 | 默认 300 秒自动终止 |

### 7.5 与 BasicChatExample 的对比

| 维度 | BasicChatExample | AgentSkillExample |
|---|---|---|
| Toolkit | 空（new Toolkit()） | 含 load_skill_through_path + 代码执行工具 |
| 工具 Schema | `[]` | 4 个工具（skill 加载 + shell + read + write） |
| 系统提示 | 固定 sysPrompt | sysPrompt + 技能目录 XML + 代码执行指令 |
| ReAct 循环 | 1 轮结束（无工具调用） | 多轮（LLM 调用多个工具实现技能创建） |
| Hook 链 | 无 | 含 SkillHook(priority=85) |

### 7.6 相关文件索引

| 文件 | 路径 |
|---|---|
| AgentSkillExample | `agentscope-examples/.../quickstart/AgentSkillExample.java` |
| SkillBox | `agentscope-core/.../skill/SkillBox.java` |
| AgentSkill | `agentscope-core/.../skill/AgentSkill.java` |
| SkillRegistry | `agentscope-core/.../skill/SkillRegistry.java` |
| RegisteredSkill | `agentscope-core/.../skill/RegisteredSkill.java` |
| SkillHook | `agentscope-core/.../skill/SkillHook.java` |
| SkillToolFactory | `agentscope-core/.../skill/SkillToolFactory.java` |
| AgentSkillPromptProvider | `agentscope-core/.../skill/AgentSkillPromptProvider.java` |
| FileSystemSkillRepository | `agentscope-core/.../skill/repository/FileSystemSkillRepository.java` |
| SkillFileSystemHelper | `agentscope-core/.../skill/util/SkillFileSystemHelper.java` |
| MarkdownSkillParser | `agentscope-core/.../skill/util/MarkdownSkillParser.java` |
| ShellCommandTool | `agentscope-core/.../tool/coding/ShellCommandTool.java` |
| ReadFileTool | (agentscope-core tool/file 包) |
| WriteFileTool | (agentscope-core tool/file 包) |
| ReActAgent | `agentscope-core/.../ReActAgent.java` |

---

> 生成时间：2026-06-02
>
> 代码版本：`agentscope-java` 1.1.0-SNAPSHOT
>
> 与 `BasicChatExample.md` 配套阅读，可对比理解"空 toolkit 纯聊天"和"Skill 驱动的多工具协作"两种模式的差异。
