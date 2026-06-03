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

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 路由图的服务类，封装 StateGraph 的调用逻辑。
 * <p>
 * 完整流程：preprocess → routing（AgentScopeRoutingAgent 内含合并节点）→ postprocess。
 * 从图的最终状态中提取 final_answer，若不存在则回退到 merged_result。
 * </p>
 */
public class RoutingGraphService {

    private static final Logger log = LoggerFactory.getLogger(RoutingGraphService.class);

    /** 编译后的路由 StateGraph，包含 preprocess → routing → postprocess 三个节点 */
    private final CompiledGraph routingGraph;

    public RoutingGraphService(CompiledGraph routingGraph) {
        this.routingGraph = routingGraph;
    }

    /**
     * 执行完整的图路由流水线：preprocess → routing（含内部合并）→ postprocess。
     *
     * @param query 用户原始查询
     * @return 路由图执行结果，包含最终答案和完整状态
     * @throws GraphRunnerException 图执行失败时抛出
     */
    public RoutingGraphResult run(String query) throws GraphRunnerException {
        Map<String, Object> inputs = Map.of("input", query);
        Optional<OverAllState> resultOpt = routingGraph.invoke(inputs);

        if (resultOpt.isEmpty()) {
            return new RoutingGraphResult(query, null, "No result from graph.");
        }

        OverAllState state = resultOpt.get();
        String finalAnswer =
                state.value("final_answer")
                        .map(Object::toString)
                        .orElse(
                                state.value("merged_result")
                                        .map(Object::toString)
                                        .orElse("No result."));

        log.debug("Routing graph completed, answer length={}", finalAnswer.length());

        return new RoutingGraphResult(query, state, finalAnswer);
    }

    /**
     * 路由图执行结果记录。
     *
     * @param query      用户原始查询
     * @param state      路由图的完整最终状态（含所有中间输出）
     * @param finalAnswer 最终答案
     */
    public record RoutingGraphResult(String query, OverAllState state, String finalAnswer) {}
}
