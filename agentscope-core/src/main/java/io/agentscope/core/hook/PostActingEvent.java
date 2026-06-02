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
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.Toolkit;

/**
 * 工具执行完成后触发的事件。
 *
 * <p>此事件允许 Hook 在结果返回给 Agent 之前检查或修改工具执行结果。
 * 适用于结果转换、错误处理或结果记录。
 *
 * <p><b>可修改:</b> 是(工具结果)
 *
 * <p>Event fired after tool execution completes.
 *
 * <p>This event allows hooks to inspect or modify the tool execution result
 * before it is returned to the agent. Useful for result transformation,
 * error handling, or result logging.
 *
 * <p><b>Modifiable:</b> Yes (tool result)
 *
 * @see PreActingEvent
 * @see ActingChunkEvent
 */
public final class PostActingEvent extends ActingEvent {

    private ToolResultBlock toolResult;
    private Msg toolResultMsg;
    private boolean stopRequested = false;

    /**
     * PostActingEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param toolkit Toolkit 实例(不能为 null)
     * @param toolUse 原始工具调用(对于空事件可为 null)
     * @param toolResult 工具执行结果(对于空事件可为 null)
     *
     * <p>Constructor for PostActingEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param toolkit The toolkit instance (must not be null)
     * @param toolUse The original tool call (can be null for empty events)
     * @param toolResult The tool execution result (can be null for empty events)
     */
    public PostActingEvent(
            Agent agent, Toolkit toolkit, ToolUseBlock toolUse, ToolResultBlock toolResult) {
        super(HookEventType.POST_ACTING, agent, toolkit, toolUse);
        this.toolResult = toolResult;
    }

    /**
     * 获取工具执行结果。
     *
     * @return 工具结果块
     *
     * <p>Get the tool execution result.
     *
     * @return The tool result block
     */
    public ToolResultBlock getToolResult() {
        return toolResult;
    }

    /**
     * 修改工具执行结果。
     *
     * @param toolResult 新的工具结果
     *
     * <p>Modify the tool execution result.
     *
     * @param toolResult The new tool result
     */
    public void setToolResult(ToolResultBlock toolResult) {
        this.toolResult = toolResult;
    }

    /**
     * 请求在此执行阶段后停止 Agent。
     *
     * <p>调用时,Agent 将返回包含 ToolResultBlock 的当前消息,
     * 而不是继续执行下一个推理迭代。用户可以审查工具执行结果,
     * 然后通过调用无参的 {@code agent.call()} 恢复执行。
     *
     * <p>这实现了需要用户审查的人机协同场景。
     *
     * <p>Request to stop the agent after this acting phase.
     *
     * <p>When called, the agent will return the current message containing the ToolResultBlock
     * instead of continuing to the next reasoning iteration. The user can then review the
     * tool execution result and resume execution by calling {@code agent.call()} with no arguments.
     *
     * <p>This enables human-in-the-loop scenarios where tool execution results need
     * user review before the agent continues reasoning.
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
     * 获取工具结果消息。
     *
     * @return 工具结果消息
     *
     * <p>Get the tool result message.
     *
     * @return The tool result message
     */
    public Msg getToolResultMsg() {
        return toolResultMsg;
    }

    /**
     * 设置工具结果消息。
     *
     * @param toolResultMsg 工具结果消息
     *
     * <p>Set the tool result message.
     *
     * @param toolResultMsg The tool result message
     */
    public void setToolResultMsg(Msg toolResultMsg) {
        this.toolResultMsg = toolResultMsg;
    }
}
