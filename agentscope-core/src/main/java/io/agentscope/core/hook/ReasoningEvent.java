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
 * 与 LLM 推理相关事件的基类。
 *
 * <p>此密封类对所有与模型推理相关的事件进行分组,
 * 包括推理前({@link PreReasoningEvent})、推理后({@link PostReasoningEvent})
 * 和流式推理块事件({@link ReasoningChunkEvent})。
 *
 * <p>Base class for events related to LLM reasoning.
 *
 * <p>This sealed class groups all events related to model reasoning, including
 * pre-reasoning ({@link PreReasoningEvent}), post-reasoning ({@link PostReasoningEvent}),
 * and streaming reasoning chunk events ({@link ReasoningChunkEvent}).
 */
public abstract sealed class ReasoningEvent extends HookEvent
        permits PreReasoningEvent, PostReasoningEvent, ReasoningChunkEvent {

    private final String modelName;
    private final GenerateOptions generateOptions;

    /**
     * ReasoningEvent 的构造方法。
     *
     * @param type 事件类型(不能为 null)
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(如果使用模型默认值可为 null)
     * @throws NullPointerException 如果 type、agent 或 modelName 为 null
     *
     * <p>Constructor for ReasoningEvent.
     *
     * @param type The event type (must not be null)
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null if using model defaults)
     * @throws NullPointerException if type, agent, or modelName is null
     */
    protected ReasoningEvent(
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
