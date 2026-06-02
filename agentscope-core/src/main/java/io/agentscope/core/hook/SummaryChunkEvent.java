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

import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import java.util.Objects;

/**
 * 摘要流式传输期间触发的事件。
 *
 * <p><b>不可修改:</b> 通知型事件
 *
 * <p><b>上下文:</b>
 * <ul>
 *   <li>{@link #getAgent()} — Agent 实例</li>
 *   <li>{@link #getMemory()} — Agent 的 memory</li>
 *   <li>{@link #getModelName()} — 模型名称</li>
 *   <li>{@link #getGenerateOptions()} — 生成选项</li>
 *   <li>{@link #getIncrementalChunk()} — 仅此块的增量内容</li>
 *   <li>{@link #getAccumulated()} — 迄今为止的完整累积消息</li>
 * </ul>
 *
 * <p><b>典型用途:</b>
 * <ul>
 *   <li>使用 {@link #getIncrementalChunk()} 进行增量显示(追加模式)</li>
 *   <li>使用 {@link #getAccumulated()} 进行全量显示(替换整个文本)</li>
 *   <li>实时显示流式摘要输出</li>
 *   <li>监控摘要生成进度</li>
 *   <li>记录流式内容</li>
 * </ul>
 *
 * <p><b>示例:</b>
 * <pre>{@code
 * case SummaryChunkEvent e -> {
 *     // 增量模式:仅打印新内容
 *     System.out.print(extractText(e.getIncrementalChunk()));
 *
 *     // 或全量模式:更新整个显示
 *     ui.setText(extractText(e.getAccumulated()));
 *
 *     yield Mono.just(e);
 * }
 * }</pre>
 *
 * <p>Event fired during summary streaming.
 *
 * <p><b>Modifiable:</b> No (notification-only)
 *
 * <p><b>Context:</b>
 * <ul>
 *   <li>{@link #getAgent()} - The agent instance</li>
 *   <li>{@link #getMemory()} - Agent's memory</li>
 *   <li>{@link #getModelName()} - The model name</li>
 *   <li>{@link #getGenerateOptions()} - The generation options</li>
 *   <li>{@link #getIncrementalChunk()} - Only the new content in this chunk</li>
 *   <li>{@link #getAccumulated()} - The full accumulated message so far</li>
 * </ul>
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Use {@link #getIncrementalChunk()} for incremental display (append-only)</li>
 *   <li>Use {@link #getAccumulated()} for full context display (replace entire text)</li>
 *   <li>Display streaming summary output in real-time</li>
 *   <li>Monitor summary generation progress</li>
 *   <li>Log streaming content</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * case SummaryChunkEvent e -> {
 *     // Incremental mode: print only new content
 *     System.out.print(extractText(e.getIncrementalChunk()));
 *
 *     // OR Cumulative mode: update entire display
 *     ui.setText(extractText(e.getAccumulated()));
 *
 *     yield Mono.just(e);
 * }
 * }</pre>
 */
public final class SummaryChunkEvent extends SummaryEvent {

    private final Msg incrementalChunk;
    private final Msg accumulated;

    /**
     * SummaryChunkEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(可为 null)
     * @param incrementalChunk 此流式事件中新增的增量内容(不能为 null)
     * @param accumulated 包含至今所有内容的完整累积消息(不能为 null)
     * @throws NullPointerException 如果 agent、modelName、incrementalChunk 或 accumulated 为 null
     *
     * <p>Constructor for SummaryChunkEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null)
     * @param incrementalChunk Only the new content generated in this streaming event (must not be
     *     null)
     * @param accumulated The full accumulated message containing all content generated so far (must
     *     not be null)
     * @throws NullPointerException if agent, modelName, incrementalChunk, or accumulated is null
     */
    public SummaryChunkEvent(
            Agent agent,
            String modelName,
            GenerateOptions generateOptions,
            Msg incrementalChunk,
            Msg accumulated) {
        super(HookEventType.SUMMARY_CHUNK, agent, modelName, generateOptions);
        this.incrementalChunk =
                Objects.requireNonNull(incrementalChunk, "incrementalChunk cannot be null");
        this.accumulated = Objects.requireNonNull(accumulated, "accumulated cannot be null");
    }

    /**
     * 获取此流式事件中新增的增量内容。
     *
     * @return 增量块
     *
     * <p>Get only the new content generated in this streaming event.
     *
     * @return The incremental chunk
     */
    public Msg getIncrementalChunk() {
        return incrementalChunk;
    }

    /**
     * 获取包含至今所有内容的完整累积消息。
     *
     * @return 累积消息
     *
     * <p>Get the full accumulated message containing all content generated so far.
     *
     * @return The accumulated message
     */
    public Msg getAccumulated() {
        return accumulated;
    }
}
