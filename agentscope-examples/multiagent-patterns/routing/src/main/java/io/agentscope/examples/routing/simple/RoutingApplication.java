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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot 启动入口，启动路由（简单模式）示例应用。
 * <p>
 * 演示流程：用户查询 → LLM 路由分类 → 并行调用专业子 Agent → 结果汇总合成。
 * 开启 {@code routing.runner.enabled=true} 即可在启动时自动运行演示查询。
 * </p>
 */
@SpringBootApplication
public class RoutingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingApplication.class, args);
    }

    /**
     * 注册应用启动完成事件监听器，在 Spring Boot 完全启动后打印欢迎信息。
     * 用于提示用户路由（简单模式）示例已成功启动。
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(
            Environment environment) {
        return event -> {
            System.out.println("\n🎉========================================🎉");
            System.out.println("✅ Routing (simple) example has started!");
            System.out.println("🎉========================================🎉\n");
        };
    }
}
