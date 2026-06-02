/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.harness.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@code tools.json} 中 {@code mcpServers.<name>} 下的 MCP 服务器配置项。
 * 镜像标准 MCP 客户端配置结构，使工作空间可以在 AgentScope 和其他 MCP 工具之间
 * 移动而无需大量修改。字段语义直接映射到 {@link io.agentscope.core.tool.mcp.McpClientBuilder}。
 * One MCP server entry under {@code mcpServers.<name>} in {@code tools.json}.
 *
 * <p>Mirrors the standard MCP client configuration shape so that workspaces can be moved between
 * AgentScope and other MCP-aware tools with minimal edits. Field semantics map directly onto
 * {@link io.agentscope.core.tool.mcp.McpClientBuilder}.
 *
 * <p>{@code transport} 是区分器（discriminator）：
 * {@code transport} is the discriminator:
 *
 * <ul>
 *   <li>{@code stdio} — 使用 {@link #command} + {@link #args} + {@link #env}.
 *   <li>{@code sse} — 使用 {@link #url} + {@link #headers} + {@link #queryParams}.
 *   <li>{@code http} — 可流式 HTTP，同上.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpServerConfig {

    /** 传输类型：{@code stdio}, {@code sse}, 或 {@code http}. */
    @JsonProperty("transport")
    private String transport;

    /** stdio：可执行命令。 */
    @JsonProperty("command")
    private String command;

    /** stdio：命令参数。 */
    @JsonProperty("args")
    private List<String> args;

    /** stdio：传递给子进程的环境变量。 */
    @JsonProperty("env")
    private Map<String, String> env;

    /** sse / http：服务器 URL。 */
    @JsonProperty("url")
    private String url;

    /** sse / http：附加到每个请求的 HTTP 头。 */
    @JsonProperty("headers")
    private Map<String, String> headers;

    /** sse / http：合并到请求 URL 的查询参数。 */
    @JsonProperty("queryParams")
    private Map<String, String> queryParams;

    /**
     * 从该服务导入工具的可选允许列表。当为 {@code null} 或为空时，
     * 注册该服务器声明的所有工具。
     */
    @JsonProperty("enableTools")
    private List<String> enableTools;

    /** 每次请求超时的 ISO-8601 时长。{@code null} 保留构建器默认值。 */
    @JsonProperty("timeout")
    private Duration timeout;

    /** 客户端初始化超时的 ISO-8601 时长。{@code null} 保留默认值。 */
    @JsonProperty("initializationTimeout")
    private Duration initializationTimeout;

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }

    public List<String> getEnableTools() {
        return enableTools;
    }

    public void setEnableTools(List<String> enableTools) {
        this.enableTools = enableTools;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getInitializationTimeout() {
        return initializationTimeout;
    }

    public void setInitializationTimeout(Duration initializationTimeout) {
        this.initializationTimeout = initializationTimeout;
    }
}
