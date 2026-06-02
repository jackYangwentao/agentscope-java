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
package io.agentscope.core.agent;

import io.agentscope.core.message.Msg;

/**
 * Complete agent interface combining all capabilities.
 *
 * <p>本接口定义了智能体的核心契约,由以下三个能力组合而成:
 * <ul>
 *   <li>{@link CallableAgent} - 处理消息并生成响应</li>
 *   <li>{@link StreamableAgent} - 在执行过程中以流式方式输出事件</li>
 *   <li>{@link ObservableAgent} - 观察消息而不产生回复(用于多 Agent 协作)</li>
 * </ul>
 *
 * <p>设计理念:
 * <ul>
 *   <li>内存(Memory)管理不属于核心 Agent 接口的职责,具体实现(如 ReActAgent)自行负责</li>
 *   <li>结构化输出是特定 Agent 提供的专项能力</li>
 *   <li>Observe 模式允许 Agent 接收消息而不回复,便于多 Agent 协作</li>
 * </ul>
 *
 * <p>This interface defines the core contract for agents, combining:
 * <ul>
 *   <li>{@link CallableAgent} - Process messages and generate responses</li>
 *   <li>{@link StreamableAgent} - Stream events during execution</li>
 *   <li>{@link ObservableAgent} - Observe messages without responding</li>
 * </ul>
 *
 * <p>Design Philosophy:
 * <ul>
 *   <li>Memory management is NOT part of the core Agent interface - it's the responsibility
 *       of specific agent implementations (e.g., ReActAgent)</li>
 *   <li>Structured output is a specialized capability provided by specific agents</li>
 *   <li>Observe pattern allows agents to receive messages without generating a reply,
 *       enabling multi-agent collaboration</li>
 * </ul>
 *
 * <p>All agents in the AgentScope framework should implement this interface.
 */
public interface Agent extends CallableAgent, StreamableAgent, ObservableAgent {

    /**
     * 获取该 Agent 的唯一标识符。
     *
     * @return Agent ID
     */
    String getAgentId();

    /**
     * 获取该 Agent 的名称。
     *
     * @return Agent name
     */
    String getName();

    /**
     * 获取该 Agent 的描述。默认实现返回 {@code "Agent(<id>) <name>"} 形式。
     *
     * @return Agent description
     */
    default String getDescription() {
        return "Agent(" + getAgentId() + ") " + getName();
    }

    /**
     * 中断当前 Agent 的执行。该方法设置一个中断标志,Agent 会在执行过程中的适当检查点检查该标志。
     * 中断是协作式的,可能不会立即生效。
     */
    void interrupt();

    /**
     * 中断当前 Agent 的执行,并附带一条用户消息。该方法设置一个中断标志,并将用户消息与中断关联。
     * 中断是协作式的,可能不会立即生效。
     *
     * @param msg 与中断关联的用户消息
     */
    void interrupt(Msg msg);
}
