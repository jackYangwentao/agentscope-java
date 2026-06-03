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
package io.agentscope.examples.supervisor;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.supervisor.tools.CalendarStubTools;
import io.agentscope.examples.supervisor.tools.EmailStubTools;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 使用 AgentScope 配置 supervisor 个人助手：DashScopeChatModel、
 * 配备 stub 工具（AgentScope @Tool）的日历和邮件 ReActAgent，
 * 以及通过 Toolkit.registration().subAgent() 委托给它们的 supervisor ReActAgent。
 */
@Configuration
public class SupervisorConfig {

    /**
     * 日历 agent 的系统提示词。指示 agent：
     * <ul>
     *   <li>解析自然语言调度请求（例如"下周二下午2点"）为 ISO 日期时间格式</li>
     *   <li>需要时使用 get_available_time_slots 检查空闲时段</li>
     *   <li>使用 create_calendar_event 安排事件</li>
     *   <li>在最终响应中始终确认已安排的内容</li>
     * </ul>
     */
    private static final String CALENDAR_AGENT_PROMPT =
            """
            You are a calendar scheduling assistant. \
            Parse natural language scheduling requests (e.g., 'next Tuesday at 2pm') \
            into proper ISO datetime formats. \
            Use get_available_time_slots to check availability when needed. \
            Use create_calendar_event to schedule events. \
            Always confirm what was scheduled in your final response.
            """;

    /**
     * 邮件 agent 的系统提示词。指示 agent：
     * <ul>
     *   <li>根据自然语言请求撰写专业邮件</li>
     *   <li>提取收件人信息并撰写合适的主题行和正文</li>
     *   <li>使用 send_email 发送消息</li>
     *   <li>在最终响应中始终确认已发送的内容</li>
     * </ul>
     */
    private static final String EMAIL_AGENT_PROMPT =
            """
            You are an email assistant. \
            Compose professional emails based on natural language requests. \
            Extract recipient information and craft appropriate subject lines and body text. \
            Use send_email to send the message. \
            Always confirm what was sent in your final response.
            """;

    /**
     * supervisor agent 的系统提示词。指示 agent：
     * <ul>
     *   <li>作为一个有用的个人助手</li>
     *   <li>可以安排日历事件和发送邮件</li>
     *   <li>将用户请求分解为适当的工具调用并协调结果</li>
     *   <li>当请求涉及多个操作时，按顺序使用多个工具</li>
     * </ul>
     */
    private static final String SUPERVISOR_PROMPT =
            """
            You are a helpful personal assistant. \
            You can schedule calendar events and send emails. \
            Break down user requests into appropriate tool calls and coordinate the results. \
            When a request involves multiple actions, use multiple tools in sequence.
            """;

    /**
     * 创建 DashScope 聊天模型（qwen-plus）。
     * 从 {@code spring.ai.dashscope.api-key} 回退到 {@code AI_DASHSCOPE_API_KEY} 环境变量。
     */
    @Bean
    public Model dashScopeChatModel(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        String key = StringUtils.hasText(apiKey) ? apiKey : System.getenv("AI_DASHSCOPE_API_KEY");
        return DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build();
    }

    /**
     * 创建日历 stub 工具的 Bean 实例。
     */
    @Bean
    public CalendarStubTools calendarStubTools() {
        return new CalendarStubTools();
    }

    /**
     * 创建邮件 stub 工具的 Bean 实例。
     */
    @Bean
    public EmailStubTools emailStubTools() {
        return new EmailStubTools();
    }

    /**
     * 创建日历 agent（ReActAgent）。配备 {@link CalendarStubTools}，
     * 以子 agent 的形式注册到 supervisor 的 Toolkit 中。
     */
    @Bean
    public ReActAgent calendarAgent(Model model, CalendarStubTools calendarStubTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(calendarStubTools);
        return ReActAgent.builder()
                .name("schedule_event")
                .description("Calendar scheduling assistant")
                .sysPrompt(CALENDAR_AGENT_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /**
     * 创建邮件 agent（ReActAgent）。配备 {@link EmailStubTools}，
     * 以子 agent 的形式注册到 supervisor 的 Toolkit 中。
     */
    @Bean
    public ReActAgent emailAgent(Model model, EmailStubTools emailStubTools) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(emailStubTools);
        return ReActAgent.builder()
                .name("manage_email")
                .description("Email assistant")
                .sysPrompt(EMAIL_AGENT_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    /**
     * 创建 supervisor 个人助手（ReActAgent）。通过
     * {@code Toolkit.registration().subAgent()} 注册日历和邮件 agent
     * 作为工具，以便 supervisor 可以根据用户请求动态调用它们。
     */
    @Bean("supervisorAgent")
    public ReActAgent supervisorAgent(
            Model model, ReActAgent calendarAgent, ReActAgent emailAgent) {
        Toolkit toolkit = new Toolkit();
        toolkit.registration().subAgent(() -> calendarAgent).apply();
        toolkit.registration().subAgent(() -> emailAgent).apply();
        return ReActAgent.builder()
                .name("personal_assistant")
                .sysPrompt(SUPERVISOR_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }
}
