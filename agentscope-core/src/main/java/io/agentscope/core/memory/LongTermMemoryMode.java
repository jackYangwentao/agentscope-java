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
package io.agentscope.core.memory;

/**
 * 定义长期记忆如何与智能体行为集成。
 *
 * <p>该枚举控制记忆管理是由框架自动处理、由智能体通过工具调用主动处理，还是两者兼有。该模式影响：
 * <ul>
 *   <li>记忆记录和检索何时发生
 *   <li>记忆管理工具是否注册到智能体的工具包中
 *   <li>智能体对其自身记忆的控制程度
 * </ul>
 *
 * <p><b>选择合适的模式：</b>
 * <ul>
 *   <li><b>AGENT_CONTROL：</b>当您希望智能体对记忆决策拥有完全自主权时使用。适用于能够理解信息重要性的高级智能体。</li>
 *   <li><b>STATIC_CONTROL：</b>当您希望框架自动管理记忆而不需要智能体参与时使用。适用于较简单的智能体或需要全面记忆的场景。</li>
 *   <li><b>BOTH：</b>推荐默认选项。结合了自动后台记忆和智能体控制，提供两全其美的方法。</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ReActAgent agent = ReActAgent.builder()
 *     .name("Assistant")
 *     .model(model)
 *     .longTermMemory(memory)
 *     .longTermMemoryMode(LongTermMemoryMode.BOTH)  // Recommended
 *     .build();
 * }</pre>
 *
 * @see LongTermMemory
 * @see io.agentscope.core.ReActAgent
 */
public enum LongTermMemoryMode {

    /**
     * Agent actively controls memory through tool calls.
     */
    AGENT_CONTROL,

    /**
     * Framework automatically manages memory without agent involvement.
     */
    STATIC_CONTROL,

    /**
     * Combines both agent control and automatic framework management.
     */
    BOTH
}
