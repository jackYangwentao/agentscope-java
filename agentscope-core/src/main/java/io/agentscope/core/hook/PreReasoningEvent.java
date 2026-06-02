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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * LLM 推理前触发的事件。
 *
 * <p>此事件允许 Hook 在消息发送到 LLM 之前检查或修改输入消息。
 * 对消息所做的更改将直接影响 LLM 的推理过程。
 *
 * <p>系统消息在每次 {@link PreReasoningEvent} 之前从冻结的基础重新注入,
 * 因此按迭代触发的 Hook 可以安全地使用 {@link #appendSystemContent(String)}。
 *
 * <p><b>可修改:</b> 是(输入消息)
 *
 * <p>Event fired before LLM reasoning.
 *
 * <p>This event allows hooks to inspect or modify the input messages before they are
 * sent to the LLM. Changes made to the messages will directly affect the LLM's reasoning.
 *
 * <p>The system message is re-injected from the frozen base before each
 * {@link PreReasoningEvent}, so per-iteration hooks can safely use
 * {@link #appendSystemContent(String)}.
 *
 * <p><b>Modifiable:</b> Yes (input messages)
 *
 * @see PostReasoningEvent
 * @see ReasoningChunkEvent
 */
public final class PreReasoningEvent extends ReasoningEvent {

    private List<Msg> inputMessages;
    private GenerateOptions overriddenGenerateOptions;

    /**
     * PreReasoningEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(可为 null)
     * @param inputMessages 发送给 LLM 的消息(不能为 null)
     * @throws NullPointerException 如果 agent、modelName 或 inputMessages 为 null
     *
     * <p>Constructor for PreReasoningEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null)
     * @param inputMessages The messages to send to LLM (must not be null)
     * @throws NullPointerException if agent, modelName, or inputMessages is null
     */
    public PreReasoningEvent(
            Agent agent,
            String modelName,
            GenerateOptions generateOptions,
            List<Msg> inputMessages) {
        super(HookEventType.PRE_REASONING, agent, modelName, generateOptions);
        this.inputMessages =
                new ArrayList<>(
                        Objects.requireNonNull(inputMessages, "inputMessages cannot be null"));
    }

    /**
     * 获取发送给 LLM 的消息列表。
     *
     * @return 输入消息列表
     *
     * <p>Get the messages to send to LLM.
     *
     * @return The input messages
     */
    public List<Msg> getInputMessages() {
        return inputMessages;
    }

    /**
     * 修改发送给 LLM 的消息列表。
     *
     * @param inputMessages 新的消息列表(不能为 null)
     * @throws NullPointerException 如果 inputMessages 为 null
     *
     * <p>Modify the messages to send to LLM.
     *
     * @param inputMessages The new message list (must not be null)
     * @throws NullPointerException if inputMessages is null
     */
    public void setInputMessages(List<Msg> inputMessages) {
        this.inputMessages = Objects.requireNonNull(inputMessages, "inputMessages cannot be null");
    }

    /**
     * 获取生效的生成选项。
     *
     * <p>如果通过 {@link #setGenerateOptions(GenerateOptions)} 设置了覆盖选项则返回覆盖值,
     * 否则返回父类中的原始选项。
     *
     * @return 生效的生成选项
     *
     * <p>Get the effective generation options.
     *
     * <p>Returns the overridden options if set via {@link #setGenerateOptions(GenerateOptions)},
     * otherwise returns the original options from the parent class.
     *
     * @return The effective generation options
     */
    public GenerateOptions getEffectiveGenerateOptions() {
        return overriddenGenerateOptions != null
                ? overriddenGenerateOptions
                : super.getGenerateOptions();
    }

    /**
     * 为此推理调用设置自定义生成选项。
     *
     * <p>允许 Hook 覆盖默认生成选项,例如为结构化输出设置特定的 tool_choice。
     *
     * @param generateOptions 自定义生成选项
     *
     * <p>Set custom generation options for this reasoning call.
     *
     * <p>This allows hooks to override the default generation options, for example to set
     * a specific tool_choice for structured output.
     *
     * @param generateOptions The custom generation options
     */
    public void setGenerateOptions(GenerateOptions generateOptions) {
        this.overriddenGenerateOptions = generateOptions;
    }
}
