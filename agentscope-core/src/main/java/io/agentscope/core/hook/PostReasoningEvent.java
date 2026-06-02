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
import io.agentscope.core.tool.ToolValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 推理完成后触发的事件。
 *
 * <p>此事件允许 Hook 在 LLM 响应被 Agent 进一步处理之前检查或修改推理结果。
 *
 * <p><b>可修改:</b> 是(推理结果)
 *
 * <p>Event fired after LLM reasoning completes.
 *
 * <p>This event allows hooks to inspect or modify the reasoning result before it is
 * processed further by the agent.
 *
 * <p><b>Modifiable:</b> Yes (reasoning result)
 *
 * @see PreReasoningEvent
 * @see ReasoningChunkEvent
 */
public final class PostReasoningEvent extends ReasoningEvent {

    private Msg reasoningMessage;
    private boolean stopRequested = false;
    private List<Msg> gotoReasoningMsgs = null;

    /**
     * PostReasoningEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param modelName 模型名称(不能为 null)
     * @param generateOptions 生成选项(可为 null)
     * @param reasoningMessage 推理结果消息(可为 null)
     *
     * <p>Constructor for PostReasoningEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param modelName The model name (must not be null)
     * @param generateOptions The generation options (may be null)
     * @param reasoningMessage The reasoning result message (may be null)
     */
    public PostReasoningEvent(
            Agent agent, String modelName, GenerateOptions generateOptions, Msg reasoningMessage) {
        super(HookEventType.POST_REASONING, agent, modelName, generateOptions);
        this.reasoningMessage = reasoningMessage;
    }

    /**
     * 获取 LLM 的推理结果消息。
     *
     * @return 推理消息,可能为 null
     *
     * <p>Get the reasoning result message from LLM.
     *
     * @return The reasoning message, may be null
     */
    public Msg getReasoningMessage() {
        return reasoningMessage;
    }

    /**
     * 修改推理结果消息。
     *
     * @param reasoningMessage 新的推理消息
     *
     * <p>Modify the reasoning result message.
     *
     * @param reasoningMessage The new reasoning message
     */
    public void setReasoningMessage(Msg reasoningMessage) {
        this.reasoningMessage = reasoningMessage;
    }

    /**
     * 请求在此推理阶段后停止 Agent。
     *
     * <p>调用时,Agent 将返回包含 ToolUseBlock 的当前消息,
     * 而不是继续执行工具。用户可以审查待处理的工具调用,
     * 然后通过调用无参的 {@code agent.call()} 恢复执行。
     *
     * <p>这实现了需要用户确认的人机协同场景。
     *
     * <p>Request to stop the agent after this reasoning phase.
     *
     * <p>When called, the agent will return the current message containing ToolUseBlocks
     * instead of proceeding to execute the tools. The user can then review the pending
     * tool calls and resume execution by calling {@code agent.call()} with no arguments.
     *
     * <p>This enables human-in-the-loop scenarios where sensitive or important tool
     * calls need user confirmation before execution.
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

    /**
     * 请求返回推理阶段,不添加任何消息。
     *
     * <p>仅在推理消息中没有待处理的 ToolUse 块时有效。
     * 如果有待处理的 ToolUse 块,将抛出 {@link IllegalStateException},
     * 因为需要先提供 ToolResult 消息。
     *
     * @throws IllegalStateException 如果有待处理的 ToolUse 块
     *
     * <p>Request to go back to reasoning phase without adding any messages.
     *
     * <p>This is only valid when there are no pending ToolUse blocks in the reasoning message.
     * If there are pending ToolUse blocks, an {@link IllegalStateException} will be thrown
     * because ToolResult messages must be provided first.
     *
     * @throws IllegalStateException if there are pending ToolUse blocks
     */
    public void gotoReasoning() {
        gotoReasoning(List.of());
    }

    /**
     * 请求返回推理阶段,携带单条消息。
     *
     * <p>如果推理消息包含 ToolUse 块,则提供的消息必须包含匹配的 ToolResult 块。
     * 也可以包含额外消息(如提示或引导)。
     *
     * @param msg 返回推理前要添加到 memory 的消息(如 ToolResult 或提示消息)
     * @throws IllegalStateException 如果 ToolResult 验证失败
     *
     * <p>Request to go back to reasoning phase with a single message.
     *
     * <p>If the reasoning message contains ToolUse blocks, the provided message must contain
     * matching ToolResult blocks. Additional messages (like hints or prompts) can also be included.
     *
     * @param msg The message to add to memory before reasoning (e.g., ToolResult or hint message)
     * @throws IllegalStateException if ToolResult validation fails
     */
    public void gotoReasoning(Msg msg) {
        gotoReasoning(List.of(msg));
    }

    /**
     * 请求返回推理阶段,携带多条消息。
     *
     * <p>验证规则:
     * <ul>
     *   <li>如果无待处理 ToolUse:消息直接添加(可以是提示/引导)</li>
     *   <li>如果有待处理 ToolUse:消息必须包含匹配的 ToolResult 块</li>
     * </ul>
     *
     * @param msgs 返回推理前要添加到 memory 的消息列表
     * @throws IllegalStateException 如果 ToolResult 验证失败
     *
     * <p>Request to go back to reasoning phase with multiple messages.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>If no pending ToolUse: messages are added as-is (can be hints/prompts)</li>
     *   <li>If pending ToolUse: messages must contain matching ToolResult blocks</li>
     * </ul>
     *
     * @param msgs The messages to add to memory before reasoning
     * @throws IllegalStateException if ToolResult validation fails
     */
    public void gotoReasoning(List<Msg> msgs) {
        ToolValidator.validateToolResultMatch(reasoningMessage, msgs);
        this.gotoReasoningMsgs = new ArrayList<>(msgs);
    }

    /**
     * 检查是否已请求返回推理。
     *
     * @return 如果已调用任何 gotoReasoning 方法则返回 true,否则返回 false
     *
     * <p>Check if a goto reasoning has been requested.
     *
     * @return true if any gotoReasoning method has been called, false otherwise
     */
    public boolean isGotoReasoningRequested() {
        return gotoReasoningMsgs != null;
    }

    /**
     * 获取返回推理前要添加的消息。
     *
     * @return 要添加的消息列表,如果未调用 gotoReasoning 则返回 null
     *
     * <p>Get the messages to add before going back to reasoning.
     *
     * @return The messages to add, or null if gotoReasoning was not called
     */
    public List<Msg> getGotoReasoningMsgs() {
        return gotoReasoningMsgs;
    }
}
