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

import java.util.Optional;
import reactor.util.context.ContextView;

/**
 * A lightweight channel that allows synchronously-invoked subagents to push their {@link Event}s
 * into the parent agent's {@code Flux<Event>} stream.
 *
 * <p>一个轻量级通道,允许以同步方式调用的子 Agent 将其 {@link Event} 推送到
 * 父 Agent 的 {@code Flux<Event>} 流中。
 *
 * <p>实例在 {@link AgentBase#createEventStream} 中创建,并以 {@link #CONTEXT_KEY}
 * 为键存入 Reactor Context。当工具方法(例如 {@code agent_spawn})在该流管道内运行时,
 * 可通过 {@link #fromContext(ContextView)} 取出总线,并将每个子事件转发到父接收器。
 *
 * <p>通过总线发出的事件 <em>必须</em> 已携带 {@link EventSource}(由调用方附加),
 * 以便下游消费者识别来源子 Agent。
 *
 * <p>当父 Agent 通过 {@code call()}(非流式)被调用时,Context 中没有总线,
 * {@link #fromContext} 返回 {@link Optional#empty()},允许调用方优雅回退到阻塞路径。
 *
 * <p>An instance is created inside {@link AgentBase#createEventStream} and stored in the Reactor
 * Context under {@link #CONTEXT_KEY}. When a tool method (e.g. {@code agent_spawn}) runs inside
 * that stream pipeline, it can retrieve the bus via {@link #fromContext(ContextView)} and forward
 * each child event to the parent sink.
 *
 * <p>Events emitted through the bus <em>must</em> already carry an {@link EventSource} (attached
 * by the caller) so downstream consumers can identify the originating subagent.
 *
 * <p>When the parent agent is invoked via {@code call()} (non-streaming), no bus is present in the
 * context and {@link #fromContext} returns {@link Optional#empty()}, allowing callers to fall back
 * gracefully to the blocking path.
 */
public interface SubagentEventBus {

    /**
     * Reactor Context 中用于存储总线实例的键。仅限内部使用;请使用
     * {@link #fromContext(ContextView)} 进行检索。
     */
    String CONTEXT_KEY = "agentscope.subagent.event.bus";

    /**
     * 将子 Agent 事件发送到父流。事件应携带非 null 的 {@link EventSource} 以标识来源子 Agent。
     *
     * <p>该方法可从任何线程安全调用;底层的 {@code FluxSink.next} 是线程安全的。
     *
     * @param event 要转发的事件(必须设置 {@link EventSource})
     */
    void emit(Event event);

    /**
     * 从 Reactor Context 中检索 {@link SubagentEventBus}(如果存在)。
     *
     * @param ctx 当前 Reactor 订阅者 Context
     * @return 包含总线的 {@link Optional};在流式管道之外运行时为空
     */
    static Optional<SubagentEventBus> fromContext(ContextView ctx) {
        if (ctx == null || !ctx.hasKey(CONTEXT_KEY)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ctx.getOrDefault(CONTEXT_KEY, null));
    }
}
