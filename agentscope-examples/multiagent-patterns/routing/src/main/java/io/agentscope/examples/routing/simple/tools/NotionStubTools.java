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
package io.agentscope.examples.routing.simple.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Notion 领域的桩工具类（文档搜索、页面获取）。
 * <p>
 * 使用 AgentScope {@code @Tool} 注解定义工具。
 * 当前返回示例数据进行演示，生产环境中应替换为真实的 Notion API 调用。
 * </p>
 */
@Component
public class NotionStubTools {

    /**
     * 在 Notion 工作空间中搜索文档。
     *
     * @param query 搜索关键词
     * @return 模拟的文档搜索结果
     */
    @Tool(name = "search_notion", description = "Search Notion workspace for documentation.")
    public String searchNotion(
            @ToolParam(name = "query", description = "Search query") String query) {
        return "Found documentation: 'API Authentication Guide' - covers OAuth2 flow, API keys, and"
                + " JWT tokens";
    }

    /**
     * 根据页面 ID 获取 Notion 页面的具体内容。
     *
     * @param pageId Notion 页面 ID
     * @return 模拟的页面内容
     */
    @Tool(name = "get_page", description = "Get a specific Notion page by ID.")
    public String getPage(
            @ToolParam(name = "pageId", description = "Notion page ID") String pageId) {
        return "Page content: Step-by-step authentication setup instructions";
    }
}
