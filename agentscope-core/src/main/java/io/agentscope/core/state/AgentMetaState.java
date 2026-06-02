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
package io.agentscope.core.state;

/**
 * 智能体元数据的状态记录。
 *
 * <p>该记录捕获智能体的基本元数据以进行持久化。由 {@link io.agentscope.core.ReActAgent}
 * 用于跨会话保存和恢复智能体配置。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * AgentMetaState state = new AgentMetaState("agent_001", "Assistant", "A helpful assistant", "You are a helpful assistant.");
 * session.save(sessionKey, "agent_meta", state);
 *
 * // Later, restore the state
 * Optional<AgentMetaState> loaded = session.get(sessionKey, "agent_meta", AgentMetaState.class);
 * }</pre>
 *
 * @param id 智能体的唯一标识符
 * @param name 智能体的显示名称
 * @param description 智能体用途的简要描述
 * @param systemPrompt 用于配置智能体行为的系统提示词
 * @see State
 * @see StateModule
 */
public record AgentMetaState(String id, String name, String description, String systemPrompt)
        implements State {}
