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
 * 摘要生成前触发的事件。
 *
 * <p>当 ReActAgent 达到最大迭代次数时触发此事件,
 * 允许 Hook 在最终摘要消息发送到 LLM 之前检查或修改输入。
 *
 * <p><b>可修改:</b> 是(输入消息)
 *
 * <p>Event fired before summary generation when max iterations is reached.
 *
 * <p>This event is fired when a ReActAgent reaches its maximum iterations,
 * allowing hooks to inspect or modify the input before the final summary
 * message is sent to the LLM.
 *
 * <p><b>Modifiable:</b> Yes (input messages)
 *
 * @see PostSummaryEvent
 * @see SummaryChunkEvent
 */
public final class PreSummaryEvent extends SummaryEvent {

    private List<Msg> inputMessages;
    private GenerateOptions overriddenGenerateOptions;
    private final int maxIterations;
    private final int currentIteration;

    /**
     * PreSummaryEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(可为 null)
     * @param inputMessages 发送给 LLM 用于摘要的消息(不能为 null)
     * @param maxIterations Agent 配置的最大迭代次数
     * @param currentIteration 触发摘要时的当前迭代次数
     * @throws NullPointerException 如果 agent、modelName 或 inputMessages 为 null
     *
     * <p>Constructor for PreSummaryEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null)
     * @param inputMessages The messages to send to LLM for summary (must not be null)
     * @param maxIterations The maximum iterations configured for the agent
     * @param currentIteration The current iteration count when summary triggered
     * @throws NullPointerException if agent, modelName, or inputMessages is null
     */
    public PreSummaryEvent(
            Agent agent,
            String modelName,
            GenerateOptions generateOptions,
            List<Msg> inputMessages,
            int maxIterations,
            int currentIteration) {
        super(HookEventType.PRE_SUMMARY, agent, modelName, generateOptions);
        this.inputMessages =
                new ArrayList<>(
                        Objects.requireNonNull(inputMessages, "inputMessages cannot be null"));
        this.maxIterations = maxIterations;
        this.currentIteration = currentIteration;
    }

    /**
     * 获取发送给 LLM 用于摘要的消息列表。
     *
     * @return 输入消息列表
     *
     * <p>Get the messages to send to LLM for summary.
     *
     * @return The input messages
     */
    public List<Msg> getInputMessages() {
        return inputMessages;
    }

    /**
     * 修改发送给 LLM 用于摘要的消息列表。
     *
     * @param inputMessages 新的消息列表(不能为 null)
     * @throws NullPointerException 如果 inputMessages 为 null
     *
     * <p>Modify the messages to send to LLM for summary.
     *
     * @param inputMessages The new message list (must not be null)
     * @throws NullPointerException if inputMessages is null
     */
    public void setInputMessages(List<Msg> inputMessages) {
        this.inputMessages = Objects.requireNonNull(inputMessages, "inputMessages cannot be null");
    }

    /**
     * 获取 Agent 配置的最大迭代次数。
     *
     * @return 最大迭代次数
     *
     * <p>Get the maximum iterations configured for the agent.
     *
     * @return The maximum iterations
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * 获取触发摘要时的当前迭代次数。
     *
     * @return 当前迭代次数
     *
     * <p>Get the current iteration count when summary was triggered.
     *
     * @return The current iteration count
     */
    public int getCurrentIteration() {
        return currentIteration;
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
     * 为此摘要调用设置自定义生成选项。
     *
     * <p>允许 Hook 覆盖默认生成选项。
     *
     * @param generateOptions 自定义生成选项
     *
     * <p>Set custom generation options for this summary call.
     *
     * <p>This allows hooks to override the default generation options.
     *
     * @param generateOptions The custom generation options
     */
    public void setGenerateOptions(GenerateOptions generateOptions) {
        this.overriddenGenerateOptions = generateOptions;
    }
}
