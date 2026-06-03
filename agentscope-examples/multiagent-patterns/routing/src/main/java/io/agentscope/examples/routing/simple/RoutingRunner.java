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
package io.agentscope.examples.routing.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 路由（简单模式）演示运行器。
 * <p>
 * 当 {@code routing.runner.enabled=true} 时自动执行一次完整的路由流程：
 * 分类 → 并行子 Agent → 结果合成。演示查询为"如何认证 API 请求？"。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "routing.runner.enabled", havingValue = "true")
public class RoutingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoutingRunner.class);

    /** 路由服务，封装了分类 → 并行子 Agent → 合成的完整流程 */
    private final RouterService routerService;

    public RoutingRunner(RouterService routerService) {
        this.routerService = routerService;
    }

    /**
     * 应用启动后自动运行的演示方法。
     * 使用预设查询"如何认证 API 请求？"触发路由流程，
     * 打印路由分类结果和最终合成答案。
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        String query = "How do I authenticate API requests?";
        log.info("Query: {}", query);
        RouterService.RouterResult result = routerService.run(query);
        log.info("Classifications:");
        result.classifications().forEach(c -> log.info("  {}: {}", c.source(), c.query()));
        log.info("---");
        log.info("Final answer:\n{}", result.finalAnswer());
    }
}
