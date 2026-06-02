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
package io.agentscope.core.hook;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 所有 Hook 事件的基类。
 *
 * <p>这是一个密封类(sealed class) — 只允许预定义的事件类型。这使得 switch 表达式
 * 中可以进行穷举模式匹配。
 *
 * <p>所有事件都提供通用上下文访问:
 * <ul>
 *   <li>{@link #getAgent()} — Agent 实例</li>
 *   <li>{@link #getMemory()} — 便捷访问 Agent 的 memory(可能为 null)</li>
 *   <li>{@link #getType()} — 事件类型</li>
 *   <li>{@link #getTimestamp()} — 事件发生时间</li>
 * </ul>
 *
 * <p><b>系统消息生命周期:</b> 每个事件携带统一的 {@code systemMsg} 字段,
 * 保存 LLM 可见的单一 {@link MsgRole#SYSTEM} 消息。{@code ReActAgent}
 * 在事件生命周期中管理该字段:
 * <ol>
 *   <li>每次 {@code call()} 开始时从 {@code sysPrompt} 播种,在 {@link PreCallEvent}
 *       Hook 运行之前。</li>
 *   <li>{@link PreCallEvent} Hook 完成后,结果系统消息被<em>冻结</em>为整个调用的基础。</li>
 *   <li>在每次 {@link PreReasoningEvent}(和 {@link PreSummaryEvent})之前,冻结的基础
 *       被重新注入到事件中 — 在这些事件上运行的 Hook 始终从相同的干净基线开始,
 *       并可按迭代追加内容。</li>
 *   <li>在调用 {@code model.stream(...)} 之前:事件的最终系统消息被前置到
 *       {@link PreReasoningEvent#getInputMessages()} 作为第一个元素。</li>
 * </ol>
 *
 * <p>由于每个 {@link PreReasoningEvent} 从冻结基础的新副本开始,按迭代触发的 Hook
 * (如子 Agent 指导)可以安全地使用 {@link #appendSystemContent(String)} —
 * 内容被追加到该迭代的副本中,不会跨迭代累积。
 *
 * <p>Hook 应通过 {@link #setSystemMessage(Msg)}、{@link #appendSystemContent(String)}
 * 或 {@link #appendSystemContent(ContentBlock)} 专门修改系统消息。
 * 直接将 {@link MsgRole#SYSTEM} 消息注入 {@code inputMessages} 是被禁止的,
 * 会在运行时抛出 {@link IllegalStateException}。
 *
 * <p><b>可修改性:</b> 事件是否允许修改取决于具体事件类中是否存在 setter 方法。
 *
 * <p>Base class for all hook events.
 *
 * <p>This is a sealed class - only the predefined event types are permitted.
 * This enables exhaustive pattern matching in switch expressions.
 *
 * <p>All events provide access to common context:
 * <ul>
 *   <li>{@link #getAgent()} - The agent instance</li>
 *   <li>{@link #getMemory()} - Convenient access to agent's memory (may be null)</li>
 *   <li>{@link #getType()} - The event type</li>
 *   <li>{@link #getTimestamp()} - When the event occurred</li>
 * </ul>
 *
 * <p><b>System message lifecycle:</b> Every event carries a unified {@code systemMsg} field
 * that holds the single {@link MsgRole#SYSTEM} message visible to the LLM. {@code ReActAgent}
 * manages this field across the event lifecycle as follows:
 * <ol>
 *   <li>Seeded from {@code sysPrompt} at the start of each {@code call()} before
 *       {@link PreCallEvent} hooks run.</li>
 *   <li>After {@link PreCallEvent} hooks complete, the resulting system message is
 *       <em>frozen</em> as the base for the entire call.</li>
 *   <li>Before each {@link PreReasoningEvent} (and {@link PreSummaryEvent}), the frozen base
 *       is injected fresh into the event — hooks that run on these events always start from
 *       the same clean baseline and may append per-iteration content.</li>
 *   <li>Before {@code model.stream(...)} is called: the event's final system message is
 *       prepended to {@link PreReasoningEvent#getInputMessages()} as the first element.</li>
 * </ol>
 *
 * <p>Because each {@link PreReasoningEvent} starts from a fresh copy of the frozen base,
 * hooks that fire per-iteration (e.g. subagent guidance) can safely use
 * {@link #appendSystemContent(String)} — content is added to that iteration's copy and never
 * accumulates across iterations.
 *
 * <p>Hooks should modify the system message exclusively via {@link #setSystemMessage(Msg)},
 * {@link #appendSystemContent(String)}, or {@link #appendSystemContent(ContentBlock)}.
 * Injecting {@link MsgRole#SYSTEM} messages into {@code inputMessages} directly is forbidden
 * and results in an {@link IllegalStateException} at runtime.
 *
 * <p><b>Modifiability:</b> Whether an event allows modification is determined by
 * the presence of setter methods in the concrete event class.
 *
 * @see Hook
 * @see HookEventType
 */
public abstract sealed class HookEvent
        permits PreCallEvent, PostCallEvent, ReasoningEvent, ActingEvent, SummaryEvent, ErrorEvent {

    private final HookEventType type;
    private final Agent agent;
    private final long timestamp;

    /**
     * 此事件的统一系统消息。Hook 通过下面的辅助方法读写此字段;
     * {@code ReActAgent} 在事件之间传递它,并在每次推理调用前将其前置到 LLM 输入中。
     *
     * <p>The unified system message for this event. Hooks read and write this field via the
     * helper methods below; {@code ReActAgent} propagates it between events and prepends it
     * to the LLM input before every reasoning call.
     */
    private Msg systemMsg;

    /**
     * HookEvent 的构造方法。
     *
     * @param type 事件类型(不能为 null)
     * @param agent Agent 实例(不能为 null)
     * @throws NullPointerException 如果 type 或 agent 为 null
     *
     * <p>Constructor for HookEvent.
     *
     * @param type The event type (must not be null)
     * @param agent The agent instance (must not be null)
     * @throws NullPointerException if type or agent is null
     */
    protected HookEvent(HookEventType type, Agent agent) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.agent = Objects.requireNonNull(agent, "agent cannot be null");
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取事件类型。
     *
     * @return 事件类型
     *
     * <p>Get the event type.
     *
     * @return The event type
     */
    public final HookEventType getType() {
        return type;
    }

    /**
     * 获取 Agent 实例。
     *
     * @return Agent 实例(永不 null)
     *
     * <p>Get the agent instance.
     *
     * @return The agent instance (never null)
     */
    public final Agent getAgent() {
        return agent;
    }

    /**
     * 获取事件创建时的时间戳。
     *
     * @return 时间戳(自 epoch 起的毫秒数)
     *
     * <p>Get the timestamp when event was created.
     *
     * @return The timestamp (milliseconds since epoch)
     */
    public final long getTimestamp() {
        return timestamp;
    }

    /**
     * 便捷访问 Agent 的 memory。
     *
     * @return memory 实例,如果 Agent 没有 memory 则返回 null
     *
     * <p>Convenient access to agent's memory.
     *
     * @return The memory, or null if agent doesn't have memory
     */
    public final Memory getMemory() {
        if (agent instanceof ReActAgent reactAgent) {
            return reactAgent.getMemory();
        }
        return null;
    }

    // ==================== System message API ====================

    /**
     * 返回当前的统一系统消息,如果未设置则返回 {@code null}。
     *
     * <p>在 {@link PreCallEvent} 和 {@link PreReasoningEvent} 上,
     * 链中较早 Hook 所做的修改已在此反映。
     *
     * @return 系统消息,可能为 null
     *
     * <p>Returns the current unified system message, or {@code null} if none has been set.
     *
     * <p>On {@link PreCallEvent} and {@link PreReasoningEvent}, modifications made by earlier
     * hooks in the chain are already reflected here.
     *
     * @return the system message, may be null
     */
    public final Msg getSystemMessage() {
        return systemMsg;
    }

    /**
     * 用给定的消息替换整个系统消息。
     *
     * <p>当只需要添加一部分系统消息时,优先使用 {@link #appendSystemContent};
     * 只有在需要设置完全自定义的系统消息时才使用此方法。
     *
     * @param systemMsg 新的系统消息(可为 null 以清除)
     *
     * <p>Replaces the entire system message with the given one.
     *
     * <p>Prefer {@link #appendSystemContent} when you only need to add a portion of the system
     * message; use this method only when you need to set a completely custom system message.
     *
     * @param systemMsg the new system message (may be null to clear)
     */
    public final void setSystemMessage(Msg systemMsg) {
        this.systemMsg = systemMsg;
    }

    /**
     * 将给定文本作为新的 {@link TextBlock} 追加到系统消息末尾。
     *
     * <p>如果系统消息尚不存在,则会自动以 {@link MsgRole#SYSTEM} 角色和名称
     * {@code "system"} 创建一条。
     *
     * @param text 要追加的文本(不能为 null)
     *
     * <p>Appends the given text as a new {@link TextBlock} at the end of the system message.
     *
     * <p>If no system message exists yet, one is created automatically with
     * {@link MsgRole#SYSTEM} and name {@code "system"}.
     *
     * @param text the text to append (must not be null)
     */
    public final void appendSystemContent(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        appendSystemContent(TextBlock.builder().text(text).build());
    }

    /**
     * 将 {@link ContentBlock} 追加到系统消息末尾。
     *
     * <p>如果系统消息尚不存在,则会自动以 {@link MsgRole#SYSTEM} 角色和名称
     * {@code "system"} 创建一条。
     *
     * @param block 要追加的内容块(不能为 null)
     *
     * <p>Appends a {@link ContentBlock} at the end of the system message.
     *
     * <p>If no system message exists yet, one is created automatically with
     * {@link MsgRole#SYSTEM} and name {@code "system"}.
     *
     * @param block the content block to append (must not be null)
     */
    public final void appendSystemContent(ContentBlock block) {
        Objects.requireNonNull(block, "block cannot be null");
        if (systemMsg == null) {
            systemMsg = Msg.builder().name("system").role(MsgRole.SYSTEM).content(block).build();
        } else {
            List<ContentBlock> merged = new ArrayList<>(systemMsg.getContent());
            merged.add(block);
            systemMsg =
                    Msg.builder()
                            .id(systemMsg.getId())
                            .name(systemMsg.getName())
                            .role(MsgRole.SYSTEM)
                            .content(merged)
                            .metadata(systemMsg.getMetadata())
                            .timestamp(systemMsg.getTimestamp())
                            .build();
        }
    }
}
