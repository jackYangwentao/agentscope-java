# Supervisor 个人助手示例

本示例实现了 **supervisor** 模式，使用 **AgentScope**。一个中央 supervisor ReActAgent
通过 `Toolkit.registration().subAgent()` 将专业 agent（日历和邮件）作为**工具**进行协调。

## 架构

- **Supervisor（主 agent）**
  接收用户请求，决定调用哪些专业 agent，并综合结果。它只看到高级工具：`schedule_event` 和 `manage_email`。

- **日历 agent**
  处理调度：解析自然语言（例如"下周二下午2点"），检查空闲情况，并创建事件。
  以工具 `schedule_event` 的形式暴露给 supervisor，接收单个字符串输入（用户的调度请求）。

- **邮件 agent**
  处理邮件：根据自然语言撰写和发送消息。
  以工具 `manage_email` 的形式暴露给 supervisor，接收单个字符串输入（用户的邮件请求）。

专业 agent 从用户角度来看是**无状态的**；supervisor 维护对话并将一次性任务委托给它们。
每个专业 agent 在聚焦的上下文中运行（自己的指令 + 请求字符串）。

## 设计选择

1. **专业 agent 作为工具**
   日历和邮件 agent 是 AgentScope ReActAgent，通过 `Toolkit.registration().subAgent()`
   注册，以便 supervisor 将它们作为工具调用（`schedule_event`、`manage_email`）。

2. **AgentScope Model**
   所有 agent 使用 **DashScopeChatModel**（AgentScope `Model`）。API 密钥来自
   `spring.ai.dashscope.api-key` 或 `AI_DASHSCOPE_API_KEY`。

3. **每个 agent 一个工具**
   每个专业 agent 对应一个工具，以实现清晰的路由和描述。

4. **Stub API**
   日历和邮件的"API"调用在 `CalendarStubTools` 和 `EmailStubTools`（AgentScope `@Tool`）
   中做了 stub 实现。生产环境中替换为真实集成。

## 项目结构

```
agentscope-examples/multiagent-patterns/supervisor/
├── README.md
├── pom.xml
└── src/main/
    ├── java/.../supervisor/
    │   ├── SupervisorApplication.java      # Spring Boot 入口
    │   ├── SupervisorConfig.java           # Model、calendarAgent、emailAgent、supervisorAgent (ReActAgent)
    │   ├── SupervisorRunner.java           # 可选的演示 runner (supervisor.run-examples=true)
    │   └── tools/
    │       ├── CalendarStubTools.java      # create_calendar_event、get_available_time_slots (@Tool)
    │       └── EmailStubTools.java         # send_email (@Tool)
    └── resources/
        └── application.yml
```

## 如何运行

### 前置条件

- JDK 17+
- Maven 3.6+
- 聊天模型的 **DashScope API 密钥**（由 supervisor 和专业 agent 使用）

设置 API 密钥：

```bash
export AI_DASHSCOPE_API_KEY=your-dashscope-api-key
```

### 构建

从仓库根目录：

```bash
./mvnw -pl agentscope-examples/multiagent-patterns/supervisor -am -B package -DskipTests
```

或从此目录：

```bash
cd agentscope-examples/multiagent-patterns/supervisor
mvn -B package -DskipTests
```

### 运行应用

默认情况下，应用启动时**不**调用模型（无演示运行）：

```bash
java -jar target/supervisor-*.jar
# 或
./mvnw -pl agentscope-examples/multiagent-patterns/supervisor spring-boot:run
```

要在启动时运行**两个演示场景**（与参考文档相同）：

1. **单领域**："Schedule a team standup for tomorrow at 9am"（仅日历）。
2. **多领域**："Schedule a meeting with the design team next Tuesday at 2pm for 1 hour, and send them an email reminder about reviewing the new mockups."（日历 + 邮件）。

设置：

```bash
export supervisor.run-examples=true
# 或在 application.yml 中添加：supervisor.run-examples: true
```

然后按上述方式启动应用。runner 将使用这两条用户消息调用 supervisor 并记录助手回复。

### 在自己的代码中使用 supervisor

注入 supervisor ReActAgent 并使用用户 `Msg` 调用它：

```java
@Qualifier("supervisorAgent")
@Autowired
ReActAgent supervisorAgent;

Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("Schedule a meeting tomorrow at 10am").build();
Msg response = supervisorAgent.call(userMsg).block();
String text = response != null ? response.getTextContent() : "";
```

## 配置

- **`spring.ai.dashscope.api-key`**
  聊天模型必需（supervisor 和专业 agent）。默认值来自 `AI_DASHSCOPE_API_KEY` 环境变量。

- **`supervisor.run-examples`**
  如果为 `true`，启动时运行两个演示场景。默认值：`false`。

## 示例流程（多领域请求）

1. 用户："Schedule a meeting with the design team next Tuesday at 2pm for 1 hour, and send them an email reminder about reviewing the new mockups."
2. Supervisor 决定调用两个工具：`schedule_event` 和 `manage_email`。
3. **schedule_event**（日历 agent）：接收调度部分，可能调用 stub 工具 `get_available_time_slots` 和 `create_calendar_event`，返回简短确认文本。
4. **manage_email**（邮件 agent）：接收邮件部分，调用 stub 工具 `send_email`，返回简短确认文本。
5. Supervisor 合并两个确认并回复用户。

这反映了参考示例的流程：supervisor 路由到专业 agent，每个 agent 在自己的指令和工具下独立运行，
只向用户展示最终的助手消息。
