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
package com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

import com.alibaba.cloud.ai.agent.agentscope.AgentScopeAgent;
import com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.node.PrepareAgentNode;
import com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.node.RetrieveNode;
import com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.node.RewriteNode;
import com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.tools.RagAgentTools;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.agentscope.core.tool.Toolkit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 使用 AgentScope 的 RAG agent 工作流配置：rewrite → retrieve → prepare_agent → agent。
 * 使用 DashScopeChatModel、AgentScope Knowledge（DashScopeTextEmbedding + InMemoryStore + SimpleKnowledge）、
 * RagAgentTools 中的 AgentScope @Tool 以及 AgentScopeAgent 作为 agent 节点。
 */
@Configuration
@ConditionalOnProperty(name = "workflow.rag.enabled", havingValue = "true")
public class RagAgentConfig {

    /** DashScope text-embedding-v3 模型的嵌入维度。 */
    private static final int EMBEDDING_DIMENSIONS = 1024;

    /**
     * RewriteNode LLM 的提示模板。%s 占位符会被替换为原始用户问题。
     * LLM 被指示生成简洁的、面向检索的查询，专注于 WNBA 实体（球员姓名、球队、统计类别）。
     */
    private static final String REWRITE_PROMPT =
            """
            Rewrite this query to retrieve relevant WNBA information.
            The knowledge base contains: team rosters, game results with scores, and player statistics (PPG, RPG, APG).
            Focus on specific player names, team names, or stat categories mentioned.
            Original query: %s
            Respond with only the rewritten query, nothing else.
            """;

    /**
     * 最终 RAG agent 回答的提示模板。第一个 %s 是检索到的上下文（拼接的文档），
     * 第二个 %s 是原始用户问题。agent 被指示仅使用提供的上下文，
     * 并在缺乏信息时如实告知。
     */
    private static final String AGENT_PROMPT =
            """
            You are a WNBA stats assistant. Use the provided context to answer questions.
            Context:
            %s

            Question: %s

            Respond concisely. If the context doesn't contain the answer, say so.
            """;

    /**
     * 创建用于查询重写和 RAG agent 的 DashScope 聊天模型（qwen-plus）。
     * 从 {@code spring.ai.dashscope.api-key} 回退到 {@code AI_DASHSCOPE_API_KEY} 环境变量。
     */
    @Bean
    public Model ragDashScopeChatModel(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        String key = StringUtils.hasText(apiKey) ? apiKey : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
    }

    /**
     * 创建 DashScope 文本嵌入模型（text-embedding-v3，1024 维），
     * 用于将文档和查询转换为向量表示以进行相似性搜索。
     */
    @Bean
    public EmbeddingModel ragEmbeddingModel(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        String key = StringUtils.hasText(apiKey) ? apiKey : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeTextEmbedding.builder()
                .apiKey(key)
                .modelName("text-embedding-v3")
                .dimensions(EMBEDDING_DIMENSIONS)
                .build();
    }

    /**
     * 构建 RAG 知识库：一个内存向量存储，预填充了 WNBA 示例文档
     *（球队名单、比赛结果、球员数据）。Knowledge 抽象处理嵌入生成和
     * ANN（近似最近邻）搜索。
     */
    @Bean
    public Knowledge ragKnowledge(EmbeddingModel ragEmbeddingModel) throws Exception {
        VDBStoreBase vectorStore = InMemoryStore.builder().dimensions(EMBEDDING_DIMENSIONS).build();
        Knowledge knowledge =
                SimpleKnowledge.builder()
                        .embeddingModel(ragEmbeddingModel)
                        .embeddingStore(vectorStore)
                        .build();
        List<Document> docs = buildSampleDocuments();
        knowledge.addDocuments(docs).block();
        return knowledge;
    }

    private static List<Document> buildSampleDocuments() {
        return List.of(
                doc(
                        "New York Liberty 2024 roster: Breanna Stewart, Sabrina Ionescu, Jonquel"
                                + " Jones, Courtney Vandersloot.",
                        "rosters",
                        0),
                doc(
                        "Las Vegas Aces 2024 roster: A'ja Wilson, Kelsey Plum, Jackie Young,"
                                + " Chelsea Gray.",
                        "rosters",
                        1),
                doc(
                        "Indiana Fever 2024 roster: Caitlin Clark, Aliyah Boston, Kelsey Mitchell,"
                                + " NaLyssa Smith.",
                        "rosters",
                        2),
                doc(
                        "2024 WNBA Finals: New York Liberty defeated Minnesota Lynx 3-2 to win the"
                                + " championship.",
                        "games",
                        3),
                doc(
                        "June 15, 2024: Indiana Fever 85, Chicago Sky 79. Caitlin Clark had 23"
                                + " points and 8 assists.",
                        "games",
                        4),
                doc(
                        "August 20, 2024: Las Vegas Aces 92, Phoenix Mercury 84. A'ja Wilson scored"
                                + " 35 points.",
                        "games",
                        5),
                doc(
                        "A'ja Wilson 2024 season stats: 26.9 PPG, 11.9 RPG, 2.6 BPG. Won MVP"
                                + " award.",
                        "stats",
                        6),
                doc(
                        "Caitlin Clark 2024 rookie stats: 19.2 PPG, 8.4 APG, 5.7 RPG. Won Rookie of"
                                + " the Year.",
                        "stats",
                        7),
                doc("Breanna Stewart 2024 stats: 20.4 PPG, 8.5 RPG, 3.5 APG.", "stats", 8));
    }

    private static Document doc(String text, String source, int index) {
        DocumentMetadata metadata =
                new DocumentMetadata(
                        TextBlock.builder().text(text).build(),
                        "wnba-" + index,
                        "0",
                        Map.of("source", source));
        return new Document(metadata);
    }

    /**
     * 构建并编译 RAG 工作流的 StateGraph。
     *
     * <p><b>图拓扑：</b>
     * <pre>
     * START → [rewrite] → [retrieve] → [prepare_agent] → [rag_agent] → END
     * </pre>
     *
     * <p><b>状态键：</b>
     * <ul>
     *   <li>{@code "question"} — 原始用户问题（{@link ReplaceStrategy}）</li>
     *   <li>{@code "rewritten_query"} — LLM 重写后的检索查询（{@link ReplaceStrategy}）</li>
     *   <li>{@code "documents"} — 检索到的文本块（{@link ReplaceStrategy}）</li>
     *   <li>{@code "messages"} — 累积的 Spring AI 消息（{@link AppendStrategy}）</li>
     * </ul>
     *
     * <p><b>节点类型：</b>
     * <ul>
     *   <li>{@code rewrite} — 确定性 LLM 节点（RewriteNode，无工具）</li>
     *   <li>{@code retrieve} — 纯确定性节点（向量搜索，无 LLM）</li>
     *   <li>{@code prepare_agent} — 纯确定性节点（格式化提示词）</li>
     *   <li>{@code rag_agent} — 完整 ReAct agent（AgentScopeAgent + 工具）</li>
     * </ul>
     */
    @Bean
    public CompiledGraph ragGraph(Model ragDashScopeChatModel, Knowledge ragKnowledge)
            throws GraphStateException {

        // --- 定义图状态模式 ---
        StateGraph graph =
                new StateGraph(
                        "rag_workflow",
                        () -> {
                            Map<String, KeyStrategy> strategies = new HashMap<>();
                            strategies.put("question", new ReplaceStrategy());
                            strategies.put("rewritten_query", new ReplaceStrategy());
                            strategies.put("documents", new ReplaceStrategy());
                            strategies.put("messages", new AppendStrategy(false));
                            return strategies;
                        });

        // --- 实例化图节点 ---
        RewriteNode rewriteNode = new RewriteNode(ragDashScopeChatModel, REWRITE_PROMPT);
        RetrieveNode retrieveNode = new RetrieveNode(ragKnowledge);
        PrepareAgentNode prepareAgentNode = new PrepareAgentNode();

        // 构建最终的 RAG ReActAgent，配备 get_latest_news 工具
        RagAgentTools ragAgentTools = new RagAgentTools();
        Toolkit agentToolkit = new Toolkit();
        agentToolkit.registerTool(ragAgentTools);
        AgentScopeAgent ragAgent =
                AgentScopeAgent.fromBuilder(
                                ReActAgent.builder()
                                        .name("rag_agent")
                                        .sysPrompt(
                                                "You are a WNBA stats assistant. Answer questions"
                                                        + " using the context provided.")
                                        .model(ragDashScopeChatModel)
                                        .toolkit(agentToolkit)
                                        .memory(new InMemoryMemory()))
                        .name("rag_agent")
                        .description("WNBA stats assistant with context")
                        // 使用 {input} 占位符 —— PrepareAgentNode 将格式化后的
                        // 提示词写入图状态的 "input" 中
                        .instruction("Answer based on the context and question below.\n\n{input}")
                        .includeContents(false)
                        .returnReasoningContents(false)
                        .build();

        // --- 连接图拓扑：线性管道 ---
        graph.addNode("rewrite", node_async(rewriteNode))
                .addNode("retrieve", node_async(retrieveNode))
                .addNode("prepare_agent", node_async(prepareAgentNode))
                .addNode("rag_agent", ragAgent.asNode())
                .addEdge(START, "rewrite")
                .addEdge("rewrite", "retrieve")
                .addEdge("retrieve", "prepare_agent")
                .addEdge("prepare_agent", "rag_agent")
                .addEdge("rag_agent", END);

        return graph.compile();
    }

    @Bean
    public RagAgentService ragAgentService(CompiledGraph ragGraph) {
        return new RagAgentService(ragGraph);
    }

    /**
     * 为 PrepareAgentNode 构建包含上下文和问题的 agent 提示词。
     */
    public static String buildAgentPrompt(String context, String question) {
        return AGENT_PROMPT.formatted(context, question);
    }
}
