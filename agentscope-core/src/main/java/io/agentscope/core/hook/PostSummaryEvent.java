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

/**
 * 摘要生成完成后触发的事件。
 *
 * <p><b>可修改:</b> 是 — {@link #setSummaryMessage(Msg)}
 *
 * <p><b>上下文:</b>
 * <ul>
 *   <li>{@link #getAgent()} — Agent 实例</li>
 *   <li>{@link #getMemory()} — Agent 的 memory</li>
 *   <li>{@link #getModelName()} — 模型名称</li>
 *   <li>{@link #getGenerateOptions()} — 生成选项</li>
 *   <li>{@link #getSummaryMessage()} — 摘要结果(可修改)</li>
 * </ul>
 *
 * <p><b>注意:</b> 此事件在摘要生成后触发,允许 Hook 在最终摘要消息返回前修改它。
 *
 * <p><b>典型用途:</b>
 * <ul>
 *   <li>过滤或修改摘要内容</li>
 *   <li>向摘要消息添加元数据</li>
 *   <li>记录摘要结果</li>
 *   <li>通过 {@link #stopAgent()} 请求停止 Agent</li>
 * </ul>
 *
 * <p>Event fired after summary generation completes.
 *
 * <p><b>Modifiable:</b> Yes - {@link #setSummaryMessage(Msg)}
 *
 * <p><b>Context:</b>
 * <ul>
 *   <li>{@link #getAgent()} - The agent instance</li>
 *   <li>{@link #getMemory()} - Agent's memory</li>
 *   <li>{@link #getModelName()} - The model name</li>
 *   <li>{@link #getGenerateOptions()} - The generation options</li>
 *   <li>{@link #getSummaryMessage()} - The summary result (modifiable)</li>
 * </ul>
 *
 * <p><b>Note:</b> This event is fired after the summary has been generated, allowing hooks
 * to modify the final summary message before it's returned.
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Filter or modify the summary content</li>
 *   <li>Add metadata to the summary message</li>
 *   <li>Log the summary result</li>
 *   <li>Request to stop the agent via {@link #stopAgent()}</li>
 * </ul>
 */
public final class PostSummaryEvent extends SummaryEvent {

    private Msg summaryMessage;
    private boolean stopRequested = false;

    /**
     * PostSummaryEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(可为 null)
     * @param summaryMessage 摘要结果消息(可为 null)
     *
     * <p>Constructor for PostSummaryEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null)
     * @param summaryMessage The summary result message (may be null)
     */
    public PostSummaryEvent(
            Agent agent, String modelName, GenerateOptions generateOptions, Msg summaryMessage) {
        super(HookEventType.POST_SUMMARY, agent, modelName, generateOptions);
        this.summaryMessage = summaryMessage;
    }

    /**
     * 获取摘要结果消息。
     *
     * @return 摘要消息,可能为 null
     *
     * <p>Get the summary result message.
     *
     * @return The summary message, may be null
     */
    public Msg getSummaryMessage() {
        return summaryMessage;
    }

    /**
     * 修改摘要结果消息。
     *
     * @param summaryMessage 新的摘要消息
     *
     * <p>Modify the summary result message.
     *
     * @param summaryMessage The new summary message
     */
    public void setSummaryMessage(Msg summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    /**
     * 请求在此摘要阶段后停止 Agent。
     *
     * <p>调用时,Agent 将返回摘要消息作为最终结果。
     * 这主要用于与其他事件类型保持一致性;
     * 由于摘要通常是最后一个阶段,这主要作为日志或监控的信号。
     *
     * <p>Request to stop the agent after this summary phase.
     *
     * <p>When called, the agent will return the summary message as the final result.
     * This is primarily for consistency with other event types; since summary is typically
     * the last phase, this mainly serves as a signal for logging or metrics purposes.
     */
    public void stopAgent() {
        this.stopRequested = true;
    }

    /**
     * 检查是否已请求停止。
     *
     * @return 如果已调用 {@link #stopAgent()} 则返回 true,否则返回 false
     *
     * <p>Check if a stop has been requested.
     *
     * @return true if {@link #stopAgent()} has been called, false otherwise
     */
    public boolean isStopRequested() {
        return stopRequested;
    }
}
