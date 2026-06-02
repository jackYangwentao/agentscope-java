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

import io.agentscope.core.message.Msg;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 长期记忆实现的抽象基类。
 *
 * <p>该类提供了一个时间序列记忆管理系统，能够持久化超越单个对话会话的信息。长期记忆使智能体能够：
 * <ul>
 *   <li>跨会话记住用户偏好、习惯和个人信息
 *   <li>从过去的交互中学习并不断提升
 *   <li>为长期运行的任务或项目维护上下文
 *   <li>基于历史数据构建个性化体验
 * </ul>
 *
 * <p>该类定义了用于框架集成级别的核心记忆 API：
 * <ul>
 *   <li>{@link #record(List)} - 将消息记录到记忆（由框架调用）
 *   <li>{@link #retrieve(Msg)} - 检索相关的记忆（由框架调用）
 * </ul>
 *
 * <p>对于智能体控制的记忆操作（AGENT_CONTROL 模式），请使用 {@link LongTermMemoryTools}，
 * 该类提供了将这些核心方法适配为智能体可调用的工具函数。
 *
 * <p>所有方法均为异步，并返回 Reactor {@link Mono} 类型，以便与智能体框架进行非阻塞集成。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 创建长期记忆实例
 * LongTermMemoryBase longTermMemory = Mem0LongTermMemory.builder()
 *     .agentName("Assistant")
 *     .userName("user_123")
 *     .apiBaseUrl("http://localhost:8000")
 *     .build();
 *
 * // 在 ReActAgent 中使用
 * ReActAgent agent = ReActAgent.builder()
 *     .name("Assistant")
 *     .model(model)
 *     .longTermMemory(longTermMemory)
 *     .longTermMemoryMode(LongTermMemoryMode.BOTH)
 *     .build();
 * }</pre>
 *
 * @see LongTermMemoryMode
 * @see io.agentscope.core.ReActAgent
 */
public interface LongTermMemory {

    /**
     * Records messages to long-term memory.
     *
     * <p>This is a developer-facing method designed to be called by the framework
     * (e.g., automatically at the end of each agent reply). Implementations should
     * extract meaningful information from the messages and persist it to the underlying
     * memory store.
     *
     * <p>The method filters out null messages before processing. Empty lists are handled
     * gracefully without error.
     *
     * <p><b>Framework Integration:</b> When {@link LongTermMemoryMode#STATIC_CONTROL} or
     * {@link LongTermMemoryMode#BOTH} is configured, this method is called automatically
     * after each agent reply to record the conversation.
     *
     * @param msgs List of messages to record (null entries are filtered out)
     * @return A Mono that completes when recording is finished
     */
    Mono<Void> record(List<Msg> msgs);

    /**
     * Retrieves relevant information from long-term memory based on the input message.
     *
     * <p>This is a developer-facing method designed to be called by the framework
     * (e.g., automatically at the beginning of each agent reply). Implementations should
     * use the message content to search for relevant memories and return them as text.
     *
     * <p>The returned text is typically added to the agent's system prompt to provide
     * context from previous interactions.
     *
     * <p><b>Framework Integration:</b> When {@link LongTermMemoryMode#STATIC_CONTROL} or
     * {@link LongTermMemoryMode#BOTH} is configured, this method is called automatically
     * before each agent reasoning step to inject relevant context.
     *
     * @param msg The message to use as a query for memory retrieval
     * @return A Mono emitting the retrieved memory text (may be empty)
     */
    Mono<String> retrieve(Msg msg);
}
