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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@code workspace/tools.json} 加载的工作空间级工具配置。
 * Workspace-level tool configuration loaded from {@code workspace/tools.json}.
 *
 * <p>两个职责：Two responsibilities:
 *
 * <ul>
 *   <li>{@link #allow} / {@link #deny} — 过滤框架内置工具表面。
 *   <li>{@link #mcpServers} — 声明由外部 MCP 服务器提供的额外工具。
 * </ul>
 *
 * <p>过滤语义：当 {@code allow} 非空时，仅保留名称出现在其中的工具；
 * {@code deny} 始终优先于 {@code allow}。空值/不存在表示"此侧无过滤"。
 * Filter semantics: when {@code allow} is non-empty, only tools whose names appear in it are
 * kept; {@code deny} always wins regardless of {@code allow}. Empty/absent values mean "no
 * filtering on this side".
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolsConfig {

    /** 当非空时，仅此列表中的工具名称暴露给模型。 */
    @JsonProperty("allow")
    private List<String> allow;

    /** 名称在此列表中的工具将被移除，无论 {@link #allow} 设置如何。 */
    @JsonProperty("deny")
    private List<String> deny;

    /** MCP 服务器标识符到其连接/工具白名单配置的映射。 */
    @JsonProperty("mcpServers")
    private Map<String, McpServerConfig> mcpServers;

    public List<String> getAllow() {
        return allow;
    }

    public void setAllow(List<String> allow) {
        this.allow = allow;
    }

    public List<String> getDeny() {
        return deny;
    }

    public void setDeny(List<String> deny) {
        this.deny = deny;
    }

    public Map<String, McpServerConfig> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(Map<String, McpServerConfig> mcpServers) {
        this.mcpServers = mcpServers != null ? new LinkedHashMap<>(mcpServers) : null;
    }
}
