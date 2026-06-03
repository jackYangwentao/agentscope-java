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
package io.agentscope.examples.routing.graph;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

import com.alibaba.cloud.ai.agent.agentscope.AgentScopeAgent;
import com.alibaba.cloud.ai.agent.agentscope.flow.AgentScopeRoutingAgent;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.routing.graph.node.PostprocessNode;
import io.agentscope.examples.routing.graph.node.PreprocessNode;
import io.agentscope.examples.routing.graph.tools.GitHubStubTools;
import io.agentscope.examples.routing.graph.tools.NotionStubTools;
import io.agentscope.examples.routing.graph.tools.SlackStubTools;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由（图模式）的 Spring 配置类。
 * <p>
 * 配置 StateGraph 工作流：preprocess → LlmRoutingAgent（作为图节点）→ postprocess。
 * LlmRoutingAgent 内部包含路由决策 + 并行子 Agent + 合并节点（RoutingMergeNode）。
 * 子 Agent（GitHub、Notion、Slack）使用 AgentScopeAgent 实现。
 * </p>
 */
@Configuration
public class RoutingGraphConfig {

    /**
     * GitHub 子 Agent 的系统提示词。
     * 占位符 {@code {github_input}} 由路由节点替换为具体子查询。
     */
    private static final String GITHUB_PROMPT =
            """
            You are a GitHub expert. Answer questions about code, API references, and implementation \
            details by searching repositories, issues, and pull requests.
            Please respond to the following request: {github_input}
            """;

    /**
     * Notion 子 Agent 的系统提示词。
     * 占位符 {@code {notion_input}} 由路由节点替换为具体子查询。
     */
    private static final String NOTION_PROMPT =
            """
            You are a Notion expert. Answer questions about internal processes, policies, and team \
            documentation by searching the organization's Notion workspace.
            Please respond to the following request: {notion_input}
            """;

    /**
     * Slack 子 Agent 的系统提示词。
     * 占位符 {@code {slack_input}} 由路由节点替换为具体子查询。
     */
    private static final String SLACK_PROMPT =
            """
            You are a Slack expert. Answer questions by searching relevant threads and discussions \
            where team members have shared knowledge and solutions.
            Please respond to the following request: {slack_input}
            """;

    /**
     * 创建 AgentScope DashScope 模型实例。
     * 所有子 Agent、路由 Agent 共享此模型，API 密钥从环境变量读取。
     */
    private static Model dashScopeModel() {
        String key = System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
    }

    /**
     * 创建 GitHub 专业子 Agent Bean。
     * 注册 search_code、search_issues、search_prs 工具，outputKey 为 "github_key"。
     */
    @Bean
    public AgentScopeAgent githubAgent(GitHubStubTools githubStubTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(githubStubTools);
        ReActAgent.Builder builder =
                ReActAgent.builder()
                        .name("github")
                        .description("GitHub specialist for code, issues, and PRs")
                        .sysPrompt(GITHUB_PROMPT)
                        .model(dashScopeModel())
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory());
        return AgentScopeAgent.fromBuilder(builder)
                .name("github")
                .description("GitHub specialist for code, issues, and PRs")
                .instruction("Please respond to the following request: {github_input}.")
                .outputKey("github_key")
                .build();
    }

    /**
     * 创建 Notion 专业子 Agent Bean，outputKey 为 "notion_key"。
     */
    @Bean
    public AgentScopeAgent notionAgent(NotionStubTools notionStubTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(notionStubTools);
        ReActAgent.Builder builder =
                ReActAgent.builder()
                        .name("notion")
                        .description("Notion specialist for docs and wikis")
                        .sysPrompt(NOTION_PROMPT)
                        .model(dashScopeModel())
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory());
        return AgentScopeAgent.fromBuilder(builder)
                .name("notion")
                .description("Notion specialist for docs and wikis")
                .instruction("Please respond to the following request: {notion_input}.")
                .outputKey("notion_key")
                .build();
    }

    /**
     * 创建 Slack 专业子 Agent Bean，outputKey 为 "slack_key"。
     */
    @Bean
    public AgentScopeAgent slackAgent(SlackStubTools slackStubTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(slackStubTools);
        ReActAgent.Builder builder =
                ReActAgent.builder()
                        .name("slack")
                        .description("Slack specialist for messages and threads")
                        .sysPrompt(SLACK_PROMPT)
                        .model(dashScopeModel())
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory());
        return AgentScopeAgent.fromBuilder(builder)
                .name("slack")
                .description("Slack specialist for messages and threads")
                .instruction("Please respond to the following request: {slack_input}.")
                .outputKey("slack_key")
                .build();
    }

    /**
     * 创建 AgentScopeRoutingAgent Bean。
     * 路由 Agent 的编译图（compiled graph）将作为 StateGraph 的一个子节点嵌入。
     */
    @Bean
    public AgentScopeRoutingAgent routerAgent(
            AgentScopeAgent githubAgent, AgentScopeAgent notionAgent, AgentScopeAgent slackAgent) {
        return AgentScopeRoutingAgent.builder()
                .name("router")
                .model(dashScopeModel())
                .description(
                        "Routes queries to GitHub, Notion, and/or Slack specialists based on"
                                + " relevance.")
                .subAgents(List.of(githubAgent, notionAgent, slackAgent))
                .build();
    }

    /**
     * 创建并编译路由 StateGraph。
     * <p>
     * 图结构：
     * <pre>
     * START → preprocess → routing (AgentScopeRoutingAgent 子图) → postprocess → END
     * </pre>
     * 状态管理策略：input/query 使用 ReplaceStrategy（覆盖），messages 使用 AppendStrategy（追加），
     * 各子 Agent 输出和各元数据字段使用 ReplaceStrategy。
     * </p>
     * <p>
     * 路由节点使用 {@code routerAgent.getAndCompileGraph()} 将 AgentScopeRoutingAgent
     * 作为编译子图（CompiledGraph）嵌入主图中。
     * </p>
     */
    @Bean
    public CompiledGraph routingGraph(AgentScopeRoutingAgent routerAgent)
            throws GraphStateException {
        KeyStrategyFactory keyFactory =
                () -> {
                    Map<String, KeyStrategy> strategies = new HashMap<>();
                    strategies.put("input", new ReplaceStrategy());
                    strategies.put("query", new ReplaceStrategy());
                    strategies.put("messages", new AppendStrategy(false));
                    strategies.put("preprocess_metadata", new ReplaceStrategy());
                    strategies.put("merged_result", new ReplaceStrategy());
                    strategies.put("final_answer", new ReplaceStrategy());
                    strategies.put("postprocess_metadata", new ReplaceStrategy());
                    strategies.put("github_key", new ReplaceStrategy());
                    strategies.put("notion_key", new ReplaceStrategy());
                    strategies.put("slack_key", new ReplaceStrategy());
                    return strategies;
                };

        StateGraph graph =
                new StateGraph("routing_graph", keyFactory)
                        .addNode("preprocess", node_async(new PreprocessNode()))
                        .addNode("routing", routerAgent.getAndCompileGraph())
                        .addNode("postprocess", node_async(new PostprocessNode()))
                        .addEdge(START, "preprocess")
                        .addEdge("preprocess", "routing")
                        .addEdge("routing", "postprocess")
                        .addEdge("postprocess", END);

        return graph.compile();
    }

    /**
     * 创建 RoutingGraphService Bean，对外提供路由图的调用入口。
     */
    @Bean
    public RoutingGraphService routingGraphService(CompiledGraph routingGraph) {
        return new RoutingGraphService(routingGraph);
    }
}
