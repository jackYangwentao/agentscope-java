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
import java.util.Objects;

/**
 * Agent 完成处理后触发的事件。
 *
 * <p>此事件允许 Hook 检查或修改 Agent 的最终响应。对输出消息所做的更改
 * 将作为 Agent 调用的返回值返回调用方。
 *
 * <p><b>可修改:</b> 是(输出消息)
 *
 * <p>Event fired after an agent completes processing.
 *
 * <p>This event allows hooks to inspect or modify the agent's final response.
 * Changes made to the output message will be returned to the caller as the
 * result of the agent invocation.
 *
 * <p><b>Modifiable:</b> Yes (output message)
 *
 * @see PreCallEvent
 */
public final class PostCallEvent extends HookEvent {

    private Msg finalMessage;

    /**
     * PostCallEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param finalMessage 最终响应消息(不能为 null)
     * @throws NullPointerException 如果 agent 或 finalMessage 为 null
     *
     * <p>Constructor for PostCallEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param finalMessage The final response message (must not be null)
     * @throws NullPointerException if agent or finalMessage is null
     */
    public PostCallEvent(Agent agent, Msg finalMessage) {
        super(HookEventType.POST_CALL, agent);
        this.finalMessage = Objects.requireNonNull(finalMessage, "finalMessage cannot be null");
    }

    /**
     * 获取最终响应消息。
     *
     * @return 最终消息
     *
     * <p>Get the final response message.
     *
     * @return The final message
     */
    public Msg getFinalMessage() {
        return finalMessage;
    }

    /**
     * 修改最终响应消息。
     *
     * @param finalMessage 新的最终消息(不能为 null)
     * @throws NullPointerException 如果 finalMessage 为 null
     *
     * <p>Modify the final response message.
     *
     * @param finalMessage The new final message (must not be null)
     * @throws NullPointerException if finalMessage is null
     */
    public void setFinalMessage(Msg finalMessage) {
        this.finalMessage = Objects.requireNonNull(finalMessage, "finalMessage cannot be null");
    }
}
