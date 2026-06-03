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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Skills 演示运行器 —— 在应用启动时自动运行一次 SQL 查询演示。
 *
 * <p>仅在配置属性 {@code skills.runner.enabled=true} 时生效（通过 {@link ConditionalOnProperty} 控制）。
 * 该运行器向 SQL 助手 Agent 发送一个预设的用户查询："查找上月订单金额超过 $1000 的所有客户"，
 * 期望 Agent 能调用 {@code read_skill("sales_analytics")} 加载销售分析技能，然后生成对应的 SQL 语句。</p>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>构建一个角色为 {@link MsgRole#USER} 的消息，包含用户查询文本</li>
 *   <li>调用 {@code sqlAssistantAgent.call(userMsg).block()} 发送给 Agent 并等待响应</li>
 *   <li>从响应消息中提取文本内容并打印到日志</li>
 * </ol>
 *
 * <p>注意：这里使用了 {@code .block()} 进行阻塞调用，仅在演示/测试代码中允许，
 * 生产环境中应使用响应式（Reactive）方式调用。</p>
 */
@Component
@ConditionalOnProperty(name = "skills.runner.enabled", havingValue = "true")
public class SkillsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillsRunner.class);

    private final ReActAgent sqlAssistantAgent;

    /**
     * 通过构造方法注入 SQL 助手 Agent。
     *
     * @param sqlAssistantAgent 由 {@link SkillsConfig} 配置的 ReActAgent Bean
     */
    public SkillsRunner(ReActAgent sqlAssistantAgent) {
        this.sqlAssistantAgent = sqlAssistantAgent;
    }

    /**
     * 应用启动时执行演示流程。
     *
     * <p>发送预设查询："查找上月订单金额超过 $1000 的所有客户"，预期 Agent 会：</p>
     * <ol>
     *   <li>识别需要销售分析领域的技能</li>
     *   <li>调用 {@code read_skill("sales_analytics")} 加载技能详情</li>
     *   <li>根据技能内容中的 Schema 和业务逻辑生成 SQL 查询</li>
     *   <li>返回生成的 SQL 语句</li>
     * </ol>
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        String query =
                "Write a SQL query to find all customers who made orders over $1000 in the last"
                        + " month";
        log.info("User: {}", query);
        // 构建用户消息：角色为 USER，内容为查询文本
        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(query).build())
                        .build();
        // 调用 Agent 并阻塞等待响应（仅适用于演示/测试场景）
        Msg response = sqlAssistantAgent.call(userMsg).block();
        String text = response != null ? response.getTextContent() : "";
        log.info("Assistant: {}", text);
    }
}
