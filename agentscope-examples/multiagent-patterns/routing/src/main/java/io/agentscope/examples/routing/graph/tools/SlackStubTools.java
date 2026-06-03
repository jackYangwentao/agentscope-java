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
package io.agentscope.examples.routing.graph.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Slack 领域的桩工具类（消息搜索、线程获取）。
 * <p>
 * 使用 AgentScope {@code @Tool} 注解定义工具。
 * 当前返回示例数据进行演示，生产环境中应替换为真实的 Slack API 调用。
 * </p>
 */
@Component
public class SlackStubTools {

    /**
     * 搜索 Slack 消息和线程。
     *
     * @param query 搜索关键词
     * @return 模拟的消息搜索结果
     */
    @Tool(name = "search_slack", description = "Search Slack messages and threads.")
    public String searchSlack(
            @ToolParam(name = "query", description = "Search query") String query) {
        return "Found discussion in #engineering: 'Use Bearer tokens for API auth, see docs for"
                + " refresh flow'";
    }

    /**
     * 根据线程 ID 获取 Slack 线程内容。
     *
     * @param threadId Slack 线程 ID
     * @return 模拟的线程内容
     */
    @Tool(name = "get_thread", description = "Get a specific Slack thread.")
    public String getThread(
            @ToolParam(name = "threadId", description = "Slack thread ID") String threadId) {
        return "Thread discusses best practices for API key rotation";
    }
}
