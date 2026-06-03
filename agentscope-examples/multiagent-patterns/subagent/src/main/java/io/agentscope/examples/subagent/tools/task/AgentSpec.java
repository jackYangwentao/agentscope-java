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

import java.util.List;

/**
 * 子 agent 的规范定义，从带有 YAML front matter 的 Markdown 文件解析而来。
 * <p>
 * 与 spring ai agent spec 格式兼容。Markdown 正文作为系统提示词；
 * front matter 定义名称、描述和可选工具过滤。
 *
 */
public record AgentSpec(
        /**
         * Unique identifier for the sub-agent (used as subagent_type in Task tool).
         */
        String name,

        /**
         * Natural language description of when and how to use this agent.
         */
        String description,

        /**
         * System prompt content (markdown body, used as ReactAgent system prompt).
         */
        String systemPrompt,

        /**
         * Optional list of tool names this agent can use. Empty means all tools.
         */
        List<String> toolNames,

        /**
         * Optional model override (e.g., "sonnet", "opus"). Not yet supported.
         */
        String model) {

    /**
     * 仅使用必填字段创建最小 spec。
     */
    public static AgentSpec of(String name, String description, String systemPrompt) {
        return new AgentSpec(name, description, systemPrompt, List.of(), null);
    }
}
