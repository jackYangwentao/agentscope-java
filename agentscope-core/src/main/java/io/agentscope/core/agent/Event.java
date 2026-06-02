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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.agentscope.core.message.Msg;

/**
 * An event emitted during streaming agent execution.
 *
 * <p>在 Agent 流式执行期间发出的一条事件。事件以结构化方式观察 Agent 执行,
 * 为每条消息提供清晰的事件类型标识和完成状态。
 *
 * <p><b>使用示例:</b>
 * <pre>{@code
 * agent.stream(userMsg, options)
 *     .subscribe(event -> {
 *         switch (event.getType()) {
 *             case REASONING -> {
 *                 if (event.isLast()) {
 *                     System.out.println("✓ Reasoning complete");
 *                 } else {
 *                     System.out.print("...");  // Progress indicator
 *                 }
 *             }
 *             case TOOL_RESULT -> {
 *                 System.out.println("Tool: " + event.getMessage().getTextContent());
 *             }
 *         }
 *     });
 * }</pre>
 *
 * <p>Events provide a structured way to observe agent execution, with clear
 * type identification and completion status for each message.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * agent.stream(userMsg, options)
 *     .subscribe(event -> {
 *         switch (event.getType()) {
 *             case REASONING -> {
 *                 if (event.isLast()) {
 *                     System.out.println("✓ Reasoning complete");
 *                 } else {
 *                     System.out.print("...");  // Progress indicator
 *                 }
 *             }
 *             case TOOL_RESULT -> {
 *                 System.out.println("Tool: " + event.getMessage().getTextContent());
 *             }
 *         }
 *     });
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {

    private final EventType type;
    private final Msg message;
    private final boolean isLast;

    /**
     * 标识事件来源:在父 {@code stream()} 调用过程中,如果事件由嵌套的子 Agent 发出,
     * 此字段指向该子 Agent;若事件由顶层 Agent 自身发出,则为 {@code null}。
     */
    private final EventSource source;

    /**
     * 创建一条新事件(顶层 Agent — 无 source)。
     *
     * @param type 事件类型(REASONING、TOOL_RESULT 等)
     * @param message 消息内容
     * @param isLast 是否为该事件的最后/完整消息
     */
    public Event(EventType type, Msg message, boolean isLast) {
        this(type, message, isLast, null);
    }

    /**
     * 创建一条带可选 source 的新事件。
     *
     * @param type 事件类型
     * @param message 消息内容
     * @param isLast 是否为最后/完整消息
     * @param source 源子 Agent,顶层 Agent 时为 {@code null}
     */
    @JsonCreator
    public Event(
            @JsonProperty("type") EventType type,
            @JsonProperty("message") Msg message,
            @JsonProperty("isLast") boolean isLast,
            @JsonProperty("source") EventSource source) {
        this.type = type;
        this.message = message;
        this.isLast = isLast;
        this.source = source;
    }

    /**
     * 返回带有指定 source 的事件副本。原事件不会被修改(不可变副本)。
     *
     * @param source 源子 Agent 描述符
     * @return 设置了 {@code source} 的新 Event
     */
    public Event withSource(EventSource source) {
        return new Event(this.type, this.message, this.isLast, source);
    }

    /**
     * 获取事件类型。
     *
     * <p>据此判断消息种类及处理方式。
     *
     * @return 事件类型
     */
    public EventType getType() {
        return type;
    }

    /**
     * 获取消息内容。
     *
     * <p>消息包含实际数据 — 可通过 {@link Msg#getRole()}、{@link Msg#getContent()}
     * 等字段进一步查看细节。
     *
     * @return 消息
     */
    public Msg getMessage() {
        return message;
    }

    /**
     * 检查此事件是否为该消息的最后/完整消息。
     *
     * <p><b>返回值:</b>
     * <ul>
     *   <li>{@code true}: 完整消息或最后一个分片。可安全持久化、作为最终结果展示或触发下游处理。</li>
     *   <li>{@code false}: 中间分片。随后还会有相同 message ID 的事件。适合用于实时 UI 更新。</li>
     * </ul>
     *
     * <p><b>流式行为:</b>
     * 对于流式事件(例如 LLM 流式输出),会发出多条具有相同 {@link Msg#getId()} 的事件:
     * <pre>
     * Event(type=REASONING, msg(id="abc", content=[...]), isLast=false)
     * Event(type=REASONING, msg(id="abc", content=[...]), isLast=false)
     * Event(type=REASONING, msg(id="abc", content=[...]), isLast=true)  ← 最终
     * </pre>
     *
     * <p><b>非流式行为:</b>
     * 对于非流式事件,只会发出一个事件,此时 {@code isLast} 恒为 {@code true}。
     *
     * <p><b>使用示例:</b>
     * <pre>{@code
     * agent.stream(userMsg, options)
     *     .subscribe(event -> {
     *         if (event.isLast()) {
     *             // 完整消息 — 可安全持久化或处理
     *             database.save(event.getMessage());
     *         } else {
     *             // 中间分片 — 更新 UI
     *             ui.append(event.getMessage().getTextContent());
     *         }
     *     });
     * }</pre>
     *
     * @return 如果是最后分片则返回 true;若还有更多分片则返回 false
     */
    @JsonProperty("isLast")
    public boolean isLast() {
        return isLast;
    }

    /**
     * 返回源子 Agent 描述符,如果事件由顶层 Agent 发出则返回 {@code null}。
     *
     * <p>消费者可借此将子 Agent 事件路由到对应的 UI 卡片或日志通道,
     * 无需依赖带外元数据。
     *
     * @return 事件源,顶层 Agent 时为 {@code null}
     */
    public EventSource getSource() {
        return source;
    }

    /**
     * 获取消息 ID(委托给 {@link Msg#getId()})。
     *
     * <p>具有相同消息 ID 的事件属于同一逻辑消息的不同部分。可据此对流式分片分组。
     *
     * @return 消息 ID
     */
    public String getMessageId() {
        return message.getId();
    }

    @Override
    public String toString() {
        if (source != null) {
            return String.format(
                    "Event{type=%s, isLast=%s, msgId=%s, contentBlocks=%d, source=%s}",
                    type, isLast, message.getId(), message.getContent().size(), source);
        }
        return String.format(
                "Event{type=%s, isLast=%s, msgId=%s, contentBlocks=%d}",
                type, isLast, message.getId(), message.getContent().size());
    }
}
