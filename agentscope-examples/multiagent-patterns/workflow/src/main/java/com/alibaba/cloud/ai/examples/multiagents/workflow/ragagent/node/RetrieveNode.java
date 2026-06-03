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
package com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 使用重写后的查询执行向量相似性搜索的图节点。
 *
 * <p><b>目的：</b>给定 LLM 重写后的查询（在状态中存储为 {@code "rewritten_query"}），
 * 此节点通过 {@link Knowledge#retrieve} 对查询进行向量化，并返回内存向量存储中
 * 前 K 个匹配的文档。
 *
 * <p><b>关键设计点：</b>
 * <ul>
 *   <li>这是一个<b>确定性节点</b>——无需 LLM 调用，仅向量搜索。</li>
 *   <li>使用 AgentScope 的 {@link Knowledge} 抽象，处理嵌入生成 + ANN 搜索。</li>
 *   <li>将 {@code "documents"}（文本内容列表）写回图状态。</li>
 * </ul>
 */
public class RetrieveNode implements NodeAction {

    private static final int TOP_K = 5;

    private final Knowledge knowledge;

    public RetrieveNode(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 从图状态中读取重写后的查询（由 RewriteNode 设置）
        String query = state.value("rewritten_query").map(Object::toString).orElse("");

        // 2. 执行向量相似性搜索——返回前 K 个文档及其分数
        List<Document> docs =
                knowledge.retrieve(query, RetrieveConfig.builder().limit(TOP_K).build()).block();

        // 3. 从每个文档中仅提取文本内容（丢弃元数据/嵌入向量）
        List<String> docContents =
                docs != null
                        ? docs.stream()
                                .map(
                                        d ->
                                                d.getMetadata() != null
                                                        ? d.getMetadata().getContentText()
                                                        : "")
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList())
                        : List.of();

        // 4. 将文档文本写入状态的 "documents" 键中，供下一个节点（PrepareAgentNode）使用
        return Map.of("documents", docContents);
    }
}
