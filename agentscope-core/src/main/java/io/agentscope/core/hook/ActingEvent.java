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

/**
 * 与工具执行相关事件的基类。
 *
 * <p>此密封类对所有与工具执行相关的事件进行分组,
 * 包括执行前({@link PreActingEvent})、执行后({@link PostActingEvent})
 * 和流式工具执行块事件({@link ActingChunkEvent})。
 *
 * <p>Base class for events related to tool execution.
 *
 * <p>This sealed class groups all events related to tool execution, including
 * pre-acting ({@link PreActingEvent}), post-acting ({@link PostActingEvent}),
 * and streaming tool execution chunk events ({@link ActingChunkEvent}).
 */
public abstract sealed class ActingEvent extends HookEvent
        permits PreActingEvent, PostActingEvent, ActingChunkEvent {

    private final Toolkit toolkit;
    protected ToolUseBlock toolUse;

    /**
     * ActingEvent 的构造方法。
     *
     * @param type 事件类型(不能为 null)
     * @param agent Agent 实例(不能为 null)
     * @param toolkit Toolkit 实例
     * @param toolUse 正在执行的工具(对于空事件可为 null)
     *
     * <p>Constructor for ActingEvent.
     *
     * @param type The event type (must not be null)
     * @param agent The agent instance (must not be null)
     * @param toolkit The toolkit instance
     * @param toolUse The tool being executed (can be null for empty events)
     */
    protected ActingEvent(HookEventType type, Agent agent, Toolkit toolkit, ToolUseBlock toolUse) {
        super(type, agent);
        this.toolkit = toolkit;
        this.toolUse = toolUse;
    }

    /**
     * 获取 Toolkit 实例。
     *
     * @return Toolkit 实例
     *
     * <p>Get the toolkit instance.
     *
     * @return The toolkit
     */
    public final Toolkit getToolkit() {
        return toolkit;
    }

    /**
     * 获取正在执行的工具。
     *
     * @return 工具调用块
     *
     * <p>Get the tool being executed.
     *
     * @return The tool use block
     */
    public final ToolUseBlock getToolUse() {
        return toolUse;
    }
}
