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
package io.agentscope.examples.routing.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.flow.node.RoutingMergeNode;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 后处理节点：结果格式化、日志记录、元数据注入。
 * <p>
 * 从路由合并节点（RoutingMergeNode）的输出中提取 merged_result，
 * 结合预处理阶段的 traceId，生成带元数据头部的最终格式化答案。
 * 输出字段：final_answer（格式化后的答案）、postprocess_metadata（后处理元数据）。
 * </p>
 */
public class PostprocessNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(PostprocessNode.class);

    /**
     * 执行后处理逻辑。
     * 从状态中获取合并结果和预处理元数据，生成带 traceId 的格式化答案。
     */
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mergedResult =
                state.value(RoutingMergeNode.DEFAULT_MERGED_OUTPUT_KEY)
                        .map(Object::toString)
                        .orElse("No result from routing.");

        @SuppressWarnings("unchecked")
        Map<String, Object> preprocessMeta =
                (Map<String, Object>) state.value("preprocess_metadata").orElse(Map.of());

        String traceId = (String) preprocessMeta.getOrDefault("traceId", "unknown");
        String timestamp = Instant.now().toString();

        // Format final answer with metadata header
        String formatted =
                String.format(
                        """
                        --- Answer (traceId=%s) ---
                        %s
                        ---
                        Generated at: %s
                        """,
                        traceId, mergedResult, timestamp);

        log.info("Postprocess: traceId={}, result length={}", traceId, mergedResult.length());

        return Map.of(
                "final_answer",
                formatted,
                "postprocess_metadata",
                Map.of(
                        "traceId", traceId,
                        "timestamp", timestamp,
                        "resultLength", mergedResult.length()));
    }
}
