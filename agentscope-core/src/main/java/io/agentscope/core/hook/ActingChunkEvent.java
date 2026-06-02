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
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.Toolkit;
import java.util.Objects;

/**
 * 工具执行流式传输期间触发的事件。
 *
 * <p><b>不可修改:</b> 通知型事件
 *
 * <p><b>上下文:</b>
 * <ul>
 *   <li>{@link #getAgent()} — Agent 实例</li>
 *   <li>{@link #getMemory()} — Agent 的 memory</li>
 *   <li>{@link #getToolkit()} — Toolkit 实例</li>
 *   <li>{@link #getToolUse()} — 正在执行的工具</li>
 *   <li>{@link #getChunk()} — 来自 ToolEmitter 的流式块</li>
 * </ul>
 *
 * <p><b>注意:</b> 这些块由工具通过 {@link io.agentscope.core.tool.ToolEmitter} 发出。
 * 它们<em>不会</em>发送给 LLM — 只有最终返回值会被发送。
 *
 * <p><b>典型用途:</b>
 * <ul>
 *   <li>显示工具执行进度</li>
 *   <li>记录工具中间输出</li>
 *   <li>监控长时间运行的工具操作</li>
 * </ul>
 *
 * <p>Event fired during tool execution streaming.
 *
 * <p><b>Modifiable:</b> No (notification-only)
 *
 * <p><b>Context:</b>
 * <ul>
 *   <li>{@link #getAgent()} - The agent instance</li>
 *   <li>{@link #getMemory()} - Agent's memory</li>
 *   <li>{@link #getToolkit()} - The toolkit instance</li>
 *   <li>{@link #getToolUse()} - The tool being executed</li>
 *   <li>{@link #getChunk()} - The streaming chunk from ToolEmitter</li>
 * </ul>
 *
 * <p><b>Note:</b> These chunks are emitted by tools via {@link
 * io.agentscope.core.tool.ToolEmitter}. They are NOT sent to the LLM - only the final return value
 * is sent.
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Display tool execution progress</li>
 *   <li>Log intermediate tool outputs</li>
 *   <li>Monitor long-running tool operations</li>
 * </ul>
 */
public final class ActingChunkEvent extends ActingEvent {

    private final ToolResultBlock chunk;

    /**
     * ActingChunkEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param toolkit Toolkit 实例(不能为 null)
     * @param toolUse 正在执行的工具(不能为 null)
     * @param chunk 来自 ToolEmitter 的流式块(不能为 null)
     * @throws NullPointerException 如果 agent、toolkit、toolUse 或 chunk 为 null
     *
     * <p>Constructor for ActingChunkEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param toolkit The toolkit instance (must not be null)
     * @param toolUse The tool being executed (must not be null)
     * @param chunk The streaming chunk from ToolEmitter (must not be null)
     * @throws NullPointerException if agent, toolkit, toolUse, or chunk is null
     */
    public ActingChunkEvent(
            Agent agent, Toolkit toolkit, ToolUseBlock toolUse, ToolResultBlock chunk) {
        super(HookEventType.ACTING_CHUNK, agent, toolkit, toolUse);
        this.chunk = Objects.requireNonNull(chunk, "chunk cannot be null");
    }

    /**
     * 获取来自 ToolEmitter 的流式块。
     *
     * @return 流式块
     *
     * <p>Get the streaming chunk from ToolEmitter.
     *
     * @return The chunk
     */
    public ToolResultBlock getChunk() {
        return chunk;
    }
}
