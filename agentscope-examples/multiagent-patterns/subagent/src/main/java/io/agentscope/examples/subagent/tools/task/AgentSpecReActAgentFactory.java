/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.examples.subagent.tools.task;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import java.util.Map;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * 从 {@link AgentSpec} 构建 AgentScope {@link ReActAgent} 实例。
 * 使用共享的 {@link Model} 和按名称映射的默认工具；每个 spec 的
 * {@link AgentSpec#toolNames()} 过滤 agent 可用的工具（空列表表示所有工具）。
 *
 * <pre>{@code
 * Map<String, Object> toolsByName = Map.of(
 *     "glob_search", globSearchTool,
 *     "grep_search", grepSearchTool,
 *     "web_fetch", webFetchTool);
 * AgentSpecReActAgentFactory factory = AgentSpecReActAgentFactory.builder()
 *     .model(model)
 *     .defaultToolsByName(toolsByName)
 *     .build();
 * ReActAgent agent = factory.create(spec);
 * }</pre>
 */
public final class AgentSpecReActAgentFactory {

    private final Model model;
    private final Map<String, Object> defaultToolsByName;

    public AgentSpecReActAgentFactory(Model model, Map<String, Object> defaultToolsByName) {
        Assert.notNull(model, "model must not be null");
        this.model = model;
        this.defaultToolsByName =
                defaultToolsByName != null ? Map.copyOf(defaultToolsByName) : Map.of();
    }

    /**
     * 从给定的 spec 创建 ReActAgent。
     * 如果 {@link AgentSpec#toolNames()} 为空，agent 获取所有默认工具；
     * 否则仅注册名称在列表中的工具。
     */
    public ReActAgent create(AgentSpec spec) {
        Assert.notNull(spec, "spec must not be null");

        Toolkit toolkit = new Toolkit();
        List<String> toolNames = spec.toolNames();
        if (CollectionUtils.isEmpty(toolNames)) {
            defaultToolsByName.values().forEach(toolkit::registerTool);
        } else {
            for (String name : toolNames) {
                Object tool = defaultToolsByName.get(name);
                if (tool != null) {
                    toolkit.registerTool(tool);
                }
            }
        }

        return ReActAgent.builder()
                .name(spec.name())
                .description(spec.description())
                .sysPrompt(spec.systemPrompt() != null ? spec.systemPrompt() : "")
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Model model;
        private Map<String, Object> defaultToolsByName = Map.of();

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder defaultToolsByName(Map<String, Object> defaultToolsByName) {
            this.defaultToolsByName = defaultToolsByName != null ? defaultToolsByName : Map.of();
            return this;
        }

        public AgentSpecReActAgentFactory build() {
            Assert.notNull(model, "model must be provided");
            return new AgentSpecReActAgentFactory(model, defaultToolsByName);
        }
    }
}
