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
package io.agentscope.harness.agent.subagent;

import io.agentscope.core.agent.Agent;

/**
 * 为单次生成或会话创建新的子代理实例。在 {@link DefaultAgentManager} 中按 {@code agent_id} 注册；
 * 每次 {@link #create()} 调用应在需要隔离时返回全新的代理。
 *
 * <p>此类型取代了用于子代理连接的原始 {@link java.util.function.Supplier}，
 * 使调用点和映射具有自文档性。
 */
@FunctionalInterface
public interface SubagentFactory {

    /** Builds a new subagent instance (typically a new {@link io.agentscope.harness.agent.HarnessAgent}). */
    Agent create();
}
