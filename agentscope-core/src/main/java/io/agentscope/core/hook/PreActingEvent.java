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
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.Toolkit;
import java.util.Objects;

/**
 * 工具执行前触发的事件。
 *
 * <p>此事件允许 Hook 在工具被调用之前检查或修改工具调用参数。
 * 这可用于注入认证令牌、修改参数或实现基于策略的访问控制。
 *
 * <p><b>可修改:</b> 是(工具调用)
 *
 * <p>Event fired before tool execution.
 *
 * <p>This event allows hooks to inspect or modify the tool call parameters before
 * the tool is invoked. This can be used to inject authentication tokens, modify
 * parameters, or implement policy-based access control.
 *
 * <p><b>Modifiable:</b> Yes (tool use)
 *
 * @see PostActingEvent
 * @see ActingChunkEvent
 */
public final class PreActingEvent extends ActingEvent {

    /**
     * PreActingEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param toolkit Toolkit 实例(不能为 null)
     * @param toolUse 要执行的工具调用(不能为 null)
     * @throws NullPointerException 如果 agent、toolkit 或 toolUse 为 null
     *
     * <p>Constructor for PreActingEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param toolkit The toolkit instance (must not be null)
     * @param toolUse The tool call to execute (must not be null)
     * @throws NullPointerException if agent, toolkit, or toolUse is null
     */
    public PreActingEvent(Agent agent, Toolkit toolkit, ToolUseBlock toolUse) {
        super(HookEventType.PRE_ACTING, agent, toolkit, toolUse);
    }

    /**
     * 修改要执行的工具调用参数。
     *
     * @param toolUse 新的工具调用(不能为 null)
     * @throws NullPointerException 如果 toolUse 为 null
     *
     * <p>Modify the tool call parameters before execution.
     *
     * @param toolUse The new tool use (must not be null)
     * @throws NullPointerException if toolUse is null
     */
    public void setToolUse(ToolUseBlock toolUse) {
        this.toolUse = Objects.requireNonNull(toolUse, "toolUse cannot be null");
    }
}
