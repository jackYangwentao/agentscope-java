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
import io.agentscope.core.model.GenerateOptions;
import java.util.Objects;

/**
 * 与摘要生成相关事件的基类。
 *
 * <p>此密封类提供了 ReActAgent 达到最大迭代次数并生成摘要时,
 * 所有摘要事件的通用上下文:
 * <ul>
 *   <li>{@link #getModelName()} — 模型名称(如 "qwen-plus", "gpt-4")</li>
 *   <li>{@link #getGenerateOptions()} — 生成选项(temperature 等)</li>
 * </ul>
 *
 * <p>子类表示摘要过程的不同阶段:
 * <ul>
 *   <li>{@link PreSummaryEvent} — 摘要生成前</li>
 *   <li>{@link SummaryChunkEvent} — 流式传输期间</li>
 *   <li>{@link PostSummaryEvent} — 摘要生成完成后</li>
 * </ul>
 *
 * <p>Base class for summary-related events.
 *
 * <p>This sealed class provides common context for all summary events that occur
 * when a ReActAgent reaches its maximum iterations and generates a summary:
 * <ul>
 *   <li>{@link #getModelName()} - The model name (e.g., "qwen-plus", "gpt-4")</li>
 *   <li>{@link #getGenerateOptions()} - The generation options (temperature, etc.)</li>
 * </ul>
 *
 * <p>Subclasses represent different stages of the summary process:
 * <ul>
 *   <li>{@link PreSummaryEvent} - Before summary generation</li>
 *   <li>{@link SummaryChunkEvent} - During streaming</li>
 *   <li>{@link PostSummaryEvent} - After summary generation completes</li>
 * </ul>
 *
 * @see PreSummaryEvent
 * @see SummaryChunkEvent
 * @see PostSummaryEvent
 */
public abstract sealed class SummaryEvent extends HookEvent
        permits PreSummaryEvent, SummaryChunkEvent, PostSummaryEvent {

    private final String modelName;
    private final GenerateOptions generateOptions;

    /**
     * SummaryEvent 的构造方法。
     *
     * @param type 事件类型(不能为 null)
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(可为 null,使用模型默认值)
     * @throws NullPointerException 如果 type、agent 或 modelName 为 null
     *
     * <p>Constructor for SummaryEvent.
     *
     * @param type The event type (must not be null)
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null if using model defaults)
     * @throws NullPointerException if type, agent, or modelName is null
     */
    protected SummaryEvent(
            HookEventType type, Agent agent, String modelName, GenerateOptions generateOptions) {
        super(type, agent);
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
        this.generateOptions = generateOptions;
    }

    /**
     * 获取模型名称。
     *
     * @return 模型名称(如 "qwen-plus", "gpt-4")
     *
     * <p>Get the model name.
     *
     * @return The model name (e.g., "qwen-plus", "gpt-4")
     */
    public final String getModelName() {
        return modelName;
    }

    /**
     * 获取生成选项。
     *
     * @return 生成选项(temperature, maxTokens 等),如果使用模型默认值则返回 null
     *
     * <p>Get the generation options.
     *
     * @return The generation options (temperature, maxTokens, etc.), or null if using model
     *     defaults
     */
    public final GenerateOptions getGenerateOptions() {
        return generateOptions;
    }
}
