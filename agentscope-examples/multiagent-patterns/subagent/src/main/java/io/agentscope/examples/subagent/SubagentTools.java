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
package io.agentscope.examples.subagent;

import io.agentscope.core.ReActAgent;
import java.util.List;

/**
 * 保存 subagent 示例的默认 AgentScope 工具、dependency-analyzer ReActAgent
 * 以及编排器 ReActAgent builder。用于构建 AgentScopeAgent bean 和编排器图。
 */
public record SubagentTools(
        List<Object> defaultTools,
        ReActAgent dependencyAnalyzerReAct,
        ReActAgent.Builder orchestratorBuilder) {}
