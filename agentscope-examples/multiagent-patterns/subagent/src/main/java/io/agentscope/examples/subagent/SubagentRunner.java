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
package io.agentscope.examples.subagent;

import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 技术尽职调查助手的交互式聊天启动器（编排器通过图调用）。
 * 当 {@code subagent.run-interactive=true} 时运行。
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "subagent.run-interactive", havingValue = "true")
public class SubagentRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubagentRunner.class);

    private final OrchestratorService orchestratorService;

    public SubagentRunner(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("技术尽职调查助手已就绪。请输入您的请求（输入 'quit' 退出）。");
        log.info("示例：分析此代码库中 Spring 的使用情况，并研究 Spring AI 的替代方案。");
        log.info("");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nUSER: ");
                String input = scanner.nextLine();
                if (input == null || input.isBlank()) {
                    continue;
                }
                if ("quit".equalsIgnoreCase(input.trim())
                        || "exit".equalsIgnoreCase(input.trim())) {
                    log.info("再见。");
                    break;
                }

                try {
                    String response = orchestratorService.run(input);
                    log.info("ASSISTANT: {}", response != null ? response : "(no response)");
                } catch (Exception e) {
                    log.error("Error: {}", e.getMessage());
                }
            }
        }
    }
}
