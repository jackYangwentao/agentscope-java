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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 路由图演示运行器。
 * <p>
 * 当 {@code routing-graph.runner.enabled=true} 时自动执行一次完整的图路由流程：
 * 预处理 → 路由 Agent（含内部合成）→ 后处理格式输出。
 * 演示查询为"如何认证 API 请求？"，展示带 traceId 的格式化输出。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "routing-graph.runner.enabled", havingValue = "true")
public class RoutingGraphRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoutingGraphRunner.class);

    /** 路由图服务，封装 preprocess → routing → postprocess 的调用 */
    private final RoutingGraphService routingGraphService;

    public RoutingGraphRunner(RoutingGraphService routingGraphService) {
        this.routingGraphService = routingGraphService;
    }

    /**
     * 应用启动后自动运行演示查询。
     * 触发完整的图路由流水线并打印最终格式化答案。
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        String query = "How do I authenticate API requests?";
        log.info("Query: {}", query);
        RoutingGraphService.RoutingGraphResult result = routingGraphService.run(query);
        log.info("Final answer:\n{}", result.finalAnswer());
    }
}
