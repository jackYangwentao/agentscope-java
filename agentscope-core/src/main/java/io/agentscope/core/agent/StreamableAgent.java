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

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.message.Msg;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Interface for agents that support streaming events during execution.
 *
 * <p>本接口使 Agent 在处理输入时能够以流式方式实时发出执行事件。
 * 事件可包含推理步骤、工具结果以及最终输出。
 *
 * <p>流式输出可用于:
 * <ul>
 *   <li>向用户展示增量进度</li>
 *   <li>实时监控 Agent 推理过程</li>
 *   <li>构建交互式聊天界面</li>
 * </ul>
 *
 * <p>This interface enables real-time streaming of execution events as the agent
 * processes input. Events can include reasoning steps, tool results, and final output.
 *
 * <p>Streaming is useful for:
 * <ul>
 *   <li>Displaying incremental progress to users</li>
 *   <li>Monitoring agent reasoning in real-time</li>
 *   <li>Building interactive chat interfaces</li>
 * </ul>
 */
public interface StreamableAgent {

    /**
     * 在当前状态基础上流式输出执行事件,不追加新的输入。
     *
     * @param options 流式配置选项
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(StreamOptions options) {
        return stream(List.of(), options);
    }

    /**
     * 在当前状态基础上,以结构化输出模式流式输出执行事件。
     *
     * @param structuredModel 用于定义输出结构的类
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(Class<?> structuredModel) {
        return stream(List.of(), StreamOptions.defaults(), structuredModel);
    }

    /**
     * 在当前状态基础上,以结构化输出模式流式输出执行事件。
     *
     * @param options 流式配置选项
     * @param structuredModel 用于定义输出结构的类
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(StreamOptions options, Class<?> structuredModel) {
        return stream(List.of(), options, structuredModel);
    }

    /**
     * 使用默认选项,流式输出单条输入消息的执行事件。
     *
     * @param msg 输入消息
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(Msg msg) {
        return stream(msg, StreamOptions.defaults());
    }

    /**
     * 流式输出单条输入消息的执行事件。
     *
     * @param msg 输入消息
     * @param options 流式配置选项
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(Msg msg, StreamOptions options) {
        return stream(List.of(msg), options);
    }

    /**
     * 使用结构化输出模式,流式输出单条输入消息的执行事件。
     *
     * @param msg 输入消息
     * @param options 流式配置选项
     * @param structuredModel 用于定义输出结构的类
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(Msg msg, StreamOptions options, Class<?> structuredModel) {
        return stream(List.of(msg), options, structuredModel);
    }

    /**
     * 使用 JSON Schema 模式,流式输出单条输入消息的执行事件。
     *
     * @param msg 输入消息
     * @param options 流式配置选项
     * @param schema 用于定义输出结构的 JSON Schema
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(Msg msg, StreamOptions options, JsonNode schema) {
        return stream(List.of(msg), options, schema);
    }

    /**
     * 使用默认选项,流式输出多条输入消息的执行事件。
     *
     * @param msgs 输入消息
     * @return 执行期间发出的事件 Flux
     */
    default Flux<Event> stream(List<Msg> msgs) {
        return stream(msgs, StreamOptions.defaults());
    }

    /**
     * 在 Agent 处理输入的过程中,实时流式输出执行事件。这是 {@link StreamableAgent} 的核心方法,
     * 所有其他便捷方法最终都会委派到这里。
     *
     * @param msgs 输入消息
     * @param options 流式配置选项
     * @return 执行期间发出的事件 Flux
     */
    Flux<Event> stream(List<Msg> msgs, StreamOptions options);

    /**
     * 以结构化输出模式流式输出执行事件。
     *
     * @param msgs 输入消息
     * @param options 流式配置选项
     * @param structuredModel 用于定义输出结构的类
     * @return 执行期间发出的事件 Flux
     */
    Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel);

    /**
     * 以 JSON Schema 模式流式输出执行事件。
     *
     * @param msgs 输入消息
     * @param options 流式配置选项
     * @param schema 用于定义输出结构的 JSON Schema
     * @return 执行期间发出的事件 Flux
     */
    Flux<Event> stream(List<Msg> msgs, StreamOptions options, JsonNode schema);
}
