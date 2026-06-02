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
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Interface for agents that can observe messages without generating replies.
 *
 * <p>本接口使 Agent 能够在不产生回复的情况下,接收并处理来自其他 Agent 或环境的消息。
 * 常用于多 Agent 协作场景,让 Agent 之间能感知彼此的行为。
 *
 * <p>典型使用场景:
 * <ul>
 *   <li>被动监听会话流程</li>
 *   <li>在多 Agent 系统中构建共享上下文</li>
 *   <li>在 Agent 管道中实现观察者模式</li>
 * </ul>
 *
 * <p>This interface enables agents to receive and process messages from other agents
 * or the environment without responding. It's commonly used in multi-agent collaboration
 * scenarios where agents need to be aware of each other's actions.
 *
 * <p>Use cases include:
 * <ul>
 *   <li>Passive monitoring of conversation flow</li>
 *   <li>Building shared context in multi-agent systems</li>
 *   <li>Implementing observer patterns in agent pipelines</li>
 * </ul>
 */
public interface ObservableAgent {

    /**
     * 观察单条消息而不产生回复。
     *
     * @param msg 要观察的消息
     * @return 观察完成时结束的 Mono
     */
    Mono<Void> observe(Msg msg);

    /**
     * 观察多条消息而不产生回复。
     *
     * @param msgs 要观察的消息列表
     * @return 所有观察完成时结束的 Mono
     */
    Mono<Void> observe(List<Msg> msgs);
}
