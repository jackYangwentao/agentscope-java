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
package com.alibaba.cloud.ai.examples.multiagents.skills;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Skills（渐进式披露）SQL 助手示例应用的启动入口。
 *
 * <p>这是一个 Spring Boot 应用，演示 AgentScope 的 Skills 机制 —— 即"渐进式披露"
 * （Progressive Disclosure）模式。核心思想是：Agent 的 System Prompt 中仅暴露技能的简短描述，
 * 当用户问题涉及特定领域时，Agent 通过 {@code read_skill} 工具动态加载完整的技能内容
 * （数据库 Schema、业务逻辑、示例查询等），从而避免在初始上下文中加载所有技能的详细信息。</p>
 *
 * <p>本示例包含两个业务技能：</p>
 * <ul>
 *   <li><b>sales_analytics（销售分析）</b> — 客户、订单、营收相关数据库 Schema 和业务逻辑</li>
 *   <li><b>inventory_management（库存管理）</b> — 产品、仓库、库存水平相关数据库 Schema 和业务逻辑</li>
 * </ul>
 *
 * <p>启动后，应用提供一个 SQL 查询助手 Agent，根据用户问题自动决定需要加载哪个技能的详细内容，
 * 然后生成对应的 SQL 查询语句。</p>
 *
 * <p>配置属性：</p>
 * <ul>
 *   <li>{@code skills.runner.enabled} — 是否在启动时自动运行演示用例（默认为 {@code false}）</li>
 *   <li>{@code spring.ai.dashscope.api-key} 或环境变量 {@code AI_DASHSCOPE_API_KEY} — DashScope API 密钥</li>
 * </ul>
 */
@SpringBootApplication
public class SkillsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillsApplication.class, args);
    }

    /**
     * 注册应用启动完成事件监听器，在控制台打印启动成功信息。
     *
     * @param environment Spring 环境配置（保留以备后续扩展）
     * @return 应用启动完成事件监听器
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(
            Environment environment) {
        return event -> {
            System.out.println("\n🎉========================================🎉");
            System.out.println("✅ Skills (SQL assistant) example has started!");
            System.out.println("🎉========================================🎉\n");
        };
    }
}
