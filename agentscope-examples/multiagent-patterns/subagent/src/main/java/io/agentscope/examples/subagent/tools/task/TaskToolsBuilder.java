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
import io.agentscope.core.agent.CallableAgent;
import io.agentscope.core.model.Model;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 构建 AgentScope Task 和 TaskOutput 工具，子 agent 从资源加载
 * 和/或以编程方式注册。使用 {@link #addAgentResource(Resource)} 加载类路径 agent
 *（例如 {@code classpath:agents/*.md}），使用 {@link #subAgent(String, ReActAgent)}
 * 注册单个编程式 agent（如 dependency-analyzer）。调用 {@link #build()} 获取
 * {@link TaskToolsResult}，然后将 taskTool 和 taskOutputTool 注册到编排器工具包。
 */
public final class TaskToolsBuilder {

    private TaskRepository taskRepository = new DefaultTaskRepository();
    private final Map<String, CallableAgent> subAgents = new HashMap<>();
    private final List<Resource> agentResources = new ArrayList<>();
    private Model model;
    private Map<String, Object> defaultToolsByName = Map.of();

    private TaskToolsBuilder() {}

    public static TaskToolsBuilder builder() {
        return new TaskToolsBuilder();
    }

    /**
     * 设置任务仓库（后台执行必需）。
     */
    public TaskToolsBuilder taskRepository(TaskRepository taskRepository) {
        Assert.notNull(taskRepository, "taskRepository must not be null");
        this.taskRepository = taskRepository;
        return this;
    }

    /**
     * 设置用于从 agent spec 资源创建 ReActAgent 的 Model。
     */
    public TaskToolsBuilder model(Model model) {
        this.model = model;
        return this;
    }

    /**
     * 按名称设置从 spec 构建的子 agent 的默认工具。键必须与工具名称匹配
     *（例如 glob_search、grep_search、web_fetch）；值为工具实例。
     */
    public TaskToolsBuilder defaultToolsByName(Map<String, Object> defaultToolsByName) {
        this.defaultToolsByName =
                defaultToolsByName != null ? Map.copyOf(defaultToolsByName) : Map.of();
        return this;
    }

    /**
     * 添加单个子 agent（编程方式）。例如用于 dependency-analyzer。
     */
    public TaskToolsBuilder subAgent(String type, ReActAgent agent) {
        Assert.hasText(type, "type must not be empty");
        Assert.notNull(agent, "agent must not be null");
        this.subAgents.put(type, agent);
        return this;
    }

    /**
     * 添加用于加载 agent spec 的 Spring Resource（例如 classpath:agents/codebase-explorer.md）。
     */
    public TaskToolsBuilder addAgentResource(Resource resource) {
        if (resource != null) {
            this.agentResources.add(resource);
        }
        return this;
    }

    /**
     * 构建并返回 Task 和 TaskOutput 工具。从资源解析子 agent
     *（使用 {@link AgentSpecLoader} 和 {@link AgentSpecReActAgentFactory}），
     * 并与编程式子 agent 合并。
     */
    public TaskToolsResult build() {
        Assert.notNull(taskRepository, "taskRepository must be provided");

        Map<String, CallableAgent> resolved = new HashMap<>(this.subAgents);
        loadFromResourcesAndMerge(resolved);

        Assert.notEmpty(
                resolved,
                "At least one sub-agent must be configured (via subAgent or addAgentResource)");

        TaskTool taskTool = new TaskTool(resolved, taskRepository);
        TaskOutputTool taskOutputTool = new TaskOutputTool(taskRepository);
        return new TaskToolsResult(taskTool, taskOutputTool);
    }

    private void loadFromResourcesAndMerge(Map<String, CallableAgent> into) {
        if (agentResources.isEmpty()) {
            return;
        }
        Assert.notNull(model, "model must be set when using addAgentResource");
        Assert.notEmpty(
                defaultToolsByName, "defaultToolsByName must be set when using addAgentResource");

        AgentSpecReActAgentFactory factory =
                new AgentSpecReActAgentFactory(model, defaultToolsByName);

        for (Resource resource : agentResources) {
            try {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }
                AgentSpec spec = AgentSpecLoader.loadFromResource(resource);
                if (spec != null && StringUtils.hasText(spec.name())) {
                    into.put(spec.name(), factory.create(spec));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to load agent spec from " + resource, e);
            }
        }
    }

    /**
     * {@link TaskToolsBuilder#build()} 的结果：将 {@link #taskTool()} 和
     * {@link #taskOutputTool()} 注册到编排器工具包。
     */
    public record TaskToolsResult(TaskTool taskTool, TaskOutputTool taskOutputTool) {}
}
