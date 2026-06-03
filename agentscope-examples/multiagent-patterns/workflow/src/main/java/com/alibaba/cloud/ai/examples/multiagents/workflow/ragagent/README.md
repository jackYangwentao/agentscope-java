# RAG Agent 工作流

等同于 `multiagents/custom.md` 和 `multiagents/code/rag-agent-workflow.md` 的自定义 RAG 工作流。

## 流程

```
Query → Rewrite → Retrieve → Prepare → Agent → Response
```

- **Rewrite**：LLM 重写查询以优化检索（例如聚焦球员姓名、统计数据）。
- **Retrieve**：向量相似度搜索（无 LLM）。
- **Prepare**：格式化和问题为提示词。
- **Agent**：带上下文的 ReactAgent；可使用 `get_latest_news` 工具获取实时更新。

## 启用

```yaml
workflow.rag.enabled: true
workflow.runner.enabled: true  # 可选，启动时演示
```

## 运行

从仓库根目录：

```bash
./mvnw -pl agentscope-examples/multiagent-patterns/workflow spring-boot:run \
  -Dspring-boot.run.arguments="--workflow.rag.enabled=true --workflow.runner.enabled=true"
```
