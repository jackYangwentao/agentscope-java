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
 * GitHub 领域的桩工具类（代码搜索、Issues、PRs）。
 * <p>
 * 使用 AgentScope {@code @Tool} 注解定义工具，供 ReActAgent 调用。
 * 当前返回示例数据进行演示，生产环境中应替换为真实的 GitHub API 调用。
 * 通过 {@link io.agentscope.core.tool.Toolkit#registerTool(Object)} 注册到 Agent 的工具包中。
 * </p>
 */
@Component
public class GitHubStubTools {

    /**
     * 在 GitHub 仓库中搜索代码。
     *
     * @param query 搜索关键词
     * @param repo  仓库名称（可选，默认为 main）
     * @return 模拟的代码搜索结果
     */
    @Tool(name = "search_code", description = "Search code in GitHub repositories.")
    public String searchCode(
            @ToolParam(name = "query", description = "Search query") String query,
            @ToolParam(name = "repo", description = "Repository name", required = false)
                    String repo) {
        String rep = repo != null ? repo : "main";
        return "Found code matching '"
                + query
                + "' in "
                + rep
                + ": authentication middleware in src/auth.py";
    }

    /**
     * 搜索 GitHub Issues 和 Pull Requests。
     *
     * @param query 搜索关键词
     * @return 模拟的 Issues 搜索结果
     */
    @Tool(name = "search_issues", description = "Search GitHub issues and pull requests.")
    public String searchIssues(
            @ToolParam(name = "query", description = "Search query") String query) {
        return "Found 3 issues matching '"
                + query
                + "': #142 (API auth docs), #89 (OAuth flow), #203 (token refresh)";
    }

    /**
     * 搜索 Pull Requests 获取实现细节。
     *
     * @param query 搜索关键词
     * @return 模拟的 PR 搜索结果
     */
    @Tool(name = "search_prs", description = "Search pull requests for implementation details.")
    public String searchPrs(@ToolParam(name = "query", description = "Search query") String query) {
        return "PR #156 added JWT authentication, PR #178 updated OAuth scopes";
    }
}
