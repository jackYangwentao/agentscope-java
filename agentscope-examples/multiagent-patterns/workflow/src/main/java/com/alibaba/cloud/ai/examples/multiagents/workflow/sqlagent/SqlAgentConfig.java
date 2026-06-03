/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

import com.alibaba.cloud.ai.agent.agentscope.AgentScopeAgent;
import com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.node.CallGetSchemaNode;
import com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.node.ExecuteGetSchemaNode;
import com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.node.ListTablesNode;
import com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.tools.SqlTools;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * 使用 Spring AI Alibaba StateGraph 和 AgentScope Agent 的 SQL agent 工作流。
 *
 * <p><b>图流程：</b>
 * <pre>
 * START → [list_tables] → [call_get_schema (LLM)] → [get_schema (执行)] → [generate_query (ReActAgent)] → END
 * </pre>
 *
 * <p><b>节点类型：</b>
 * <ul>
 *   <li>{@code list_tables} —— 确定性节点：创建合成工具调用来列出数据库表</li>
 *   <li>{@code call_get_schema} —— LLM 节点：强制模型调用 {@code sql_db_schema} 工具</li>
 *   <li>{@code get_schema} —— 确定性节点：执行 schema 工具调用，追加结果</li>
 *   <li>{@code generate_query} —— 完整 ReAct Agent：配备 {@code sql_db_query} 工具，生成并执行 SQL</li>
 * </ul>
 *
 * <p><b>数据库：</b>H2 内存数据库，使用类似 Chinook 的模式（从 schema-chinook.sql 初始化）。
 *
 * @see SqlTools 将 JdbcTemplate 封装为 AgentScope 的 @Tool 方法
 * @see SqlAgentService 调用已编译的图
 */
@Configuration
@ConditionalOnProperty(name = "workflow.sql.enabled", havingValue = "true")
public class SqlAgentConfig {

    /** SQL 方言，用于提示词生成（H2 语法接近 PostgreSQL）。 */
    private static final String DIALECT = "H2";
    /** agent 默认返回的最大结果行数。 */
    private static final int TOP_K = 5;

    /**
     * generate_query ReActAgent 的系统提示词。指示 agent：
     * <ul>
     *   <li>根据问题和模式生成正确的 SQL</li>
     *   <li>除非另有指定，否则将结果限制为 TOP_K 条</li>
     *   <li>仅查询相关列（不使用 SELECT *）</li>
     *   <li>绝不执行 DML 语句（INSERT/UPDATE/DELETE/DROP）</li>
     * </ul>
     */
    private static final String GENERATE_QUERY_PROMPT =
            """
            You are an agent designed to interact with a SQL database.
            Given an input question, create a syntactically correct %s query to run,
            then look at the results of the query and return the answer. Unless the user
            specifies a specific number of examples they wish to obtain, always limit your
            query to at most %d results.

            You can order the results by a relevant column to return the most interesting
            examples in the database. Never query for all the columns from a specific table,
            only ask for the relevant columns given the question.

            DO NOT make any DML statements (INSERT, UPDATE, DELETE, DROP etc.) to the database.
            """
                    .formatted(DIALECT, TOP_K);

    @Bean
    public SqlTools sqlTools(JdbcTemplate jdbcTemplate) {
        return new SqlTools(jdbcTemplate);
    }

    @Bean
    public Model dashScopeChatModel(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        String key = StringUtils.hasText(apiKey) ? apiKey : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
    }

    /**
     * 构建并编译 SQL 工作流的 StateGraph。
     *
     * <p><b>状态键：</b>
     * <ul>
     *   <li>{@code "messages"} —— 累积的 Spring AI 消息（{@link AppendStrategy}）</li>
     *   <li>{@code "llm_response"} —— 用于工具执行的最后一条 LLM 响应（{@link ReplaceStrategy}）</li>
     *   <li>{@code "question"} —— 原始用户问题（{@link ReplaceStrategy}）</li>
     * </ul>
     */
    @Bean
    public CompiledGraph sqlGraph(Model model, SqlTools sqlTools) throws GraphStateException {
        // --- 定义图状态模式 ---
        StateGraph graph =
                new StateGraph(
                        "sql_workflow",
                        () -> {
                            Map<String, KeyStrategy> strategies = new HashMap<>();
                            strategies.put("messages", new AppendStrategy(false));
                            strategies.put("llm_response", new ReplaceStrategy());
                            strategies.put("question", new ReplaceStrategy());
                            return strategies;
                        });

        // --- 实例化图节点 ---
        ListTablesNode listTablesNode = new ListTablesNode(sqlTools);
        CallGetSchemaNode callGetSchemaNode = new CallGetSchemaNode(model, sqlTools);
        ExecuteGetSchemaNode executeGetSchemaNode = new ExecuteGetSchemaNode(sqlTools);

        // 构建配备 SQL 工具（list_tables, schema, query）的 generate_query ReActAgent
        Toolkit generateQueryToolkit = new Toolkit();
        generateQueryToolkit.registerTool(sqlTools);
        AgentScopeAgent generateQueryAgent =
                AgentScopeAgent.fromBuilder(
                                ReActAgent.builder()
                                        .name("generate_query")
                                        .sysPrompt(GENERATE_QUERY_PROMPT)
                                        .model(model)
                                        .toolkit(generateQueryToolkit)
                                        .memory(new InMemoryMemory()))
                        .name("generate_query")
                        .description("Generate and run SQL query")
                        // includeContents=true 将累积消息作为上下文传递，
                        // 使 agent 无需 {input} 占位符即可了解模式和问题
                        .includeContents(true)
                        .returnReasoningContents(false)
                        .build();

        // --- 连接图拓扑：线性管道 ---
        graph.addNode("list_tables", node_async(listTablesNode))
                .addNode("call_get_schema", node_async(callGetSchemaNode))
                .addNode("get_schema", node_async(executeGetSchemaNode))
                .addNode("generate_query", generateQueryAgent.asNode())
                .addEdge(START, "list_tables")
                .addEdge("list_tables", "call_get_schema")
                .addEdge("call_get_schema", "get_schema")
                .addEdge("get_schema", "generate_query")
                .addEdge("generate_query", END);

        return graph.compile();
    }

    @Bean
    public SqlAgentService sqlAgentService(CompiledGraph sqlGraph) {
        return new SqlAgentService(sqlGraph);
    }
}
