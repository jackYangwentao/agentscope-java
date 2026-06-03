# SQL Agent 工作流

使用 StateGraph 的自定义 SQL agent。

## 流程

```
START → list_tables → call_get_schema → get_schema → generate_query(ReactAgent) → END
```

- **list_tables**：确定性节点，创建合成工具调用，返回可用表
- **call_get_schema**：使用 `sql_db_schema` 工具的 LLM（强制工具选择）
- **get_schema**：ToolNode 运行 `sql_db_schema`
- **generate_query**：使用 `sql_db_query` 工具的 ReactAgent；处理 LLM↔工具循环直到最终答案

## 启用

```yaml
workflow.sql.enabled: true
workflow.runner.enabled: true  # 可选，启动时演示
```

## 数据库

使用 H2 内存数据库，模式类似 Chinook。模式从 `schema-chinook.sql` 初始化。

## 运行

从仓库根目录：

```bash
./mvnw -pl agentscope-examples/multiagent-patterns/workflow spring-boot:run \
  -Dspring-boot.run.arguments="--workflow.sql.enabled=true --workflow.runner.enabled=true"
```
