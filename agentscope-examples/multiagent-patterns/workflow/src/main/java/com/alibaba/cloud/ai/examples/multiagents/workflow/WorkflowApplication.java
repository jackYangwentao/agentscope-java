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
package com.alibaba.cloud.ai.examples.multiagents.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 多 agent 工作流示例的 Spring Boot 应用。
 *
 * <p>提供两个独立的工作流示例，每个由属性控制：
 * <ul>
 *   <li>{@code workflow.rag.enabled=true} —— RAG agent（重写 → 检索 → agent）</li>
 *   <li>{@code workflow.sql.enabled=true} —— SQL agent（列表 → 获取模式 → 生成查询）</li>
 * </ul>
 *
 * <p>两个示例均使用 <b>Spring AI Alibaba StateGraph</b> 进行图编排，
 * 以及 <b>AgentScope</b>（ReActAgent、AgentScopeAgent、Model、Knowledge、@Tool）
 * 进行 agent 执行。详见 ragagent/ 和 sqlagent/ 包。
 *
 * <p>启动时，{@link #applicationReadyEventListener} 会打印确认消息。
 * 如果 {@code workflow.runner.enabled=true}，活跃工作流的 runner
 * 将自动执行一次一次性演示。
 *
 * @see com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.RagAgentConfig
 * @see com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.SqlAgentConfig
 */
@SpringBootApplication
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(
            Environment environment) {
        return event -> {
            System.out.println("\n🎉========================================🎉");
            System.out.println("✅ Workflow (RAG / SQL agent) example has started!");
            System.out.println(
                    "   (Enable workflow.rag.enabled or workflow.sql.enabled in application.yml)");
            System.out.println("🎉========================================🎉\n");
        };
    }
}
