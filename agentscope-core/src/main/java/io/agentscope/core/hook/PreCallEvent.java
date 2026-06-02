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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agent 开始处理前触发的事件。
 *
 * <p>此事件允许 Hook 在任何处理开始之前检查或修改输入消息和系统消息。
 * 在此对消息所做的更改将影响整个 Agent 执行过程。
 *
 * <p><b>可修改:</b> 是(消息、系统消息)
 *
 * <p>Event fired before an agent starts processing.
 *
 * <p>This event allows hooks to inspect or modify the input messages and system message
 * before any processing begins. Changes made to messages here will affect the entire
 * agent execution.
 *
 * <p><b>Modifiable:</b> Yes (messages, system message)
 *
 * @see PostCallEvent
 */
public final class PreCallEvent extends HookEvent {

    private List<Msg> inputMessages;

    /**
     * PreCallEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param inputMessages Agent 的输入消息(不能为 null)
     * @throws NullPointerException 如果 agent 或 inputMessages 为 null
     *
     * <p>Constructor for PreCallEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param inputMessages The messages input to the agent (must not be null)
     * @throws NullPointerException if agent or inputMessages is null
     */
    public PreCallEvent(Agent agent, List<Msg> inputMessages) {
        super(HookEventType.PRE_CALL, agent);
        this.inputMessages =
                new ArrayList<>(
                        Objects.requireNonNull(inputMessages, "inputMessages cannot be null"));
    }

    /**
     * 获取 Agent 的输入消息列表。
     *
     * @return 输入消息列表
     *
     * <p>Get the input messages for the agent call.
     *
     * @return The input messages
     */
    public List<Msg> getInputMessages() {
        return inputMessages;
    }

    /**
     * 修改 Agent 调用的输入消息列表。
     *
     * @param inputMessages 新的消息列表(不能为 null)
     * @throws NullPointerException 如果 inputMessages 为 null
     *
     * <p>Modify the input messages for the agent call.
     *
     * @param inputMessages The new message list (must not be null)
     * @throws NullPointerException if inputMessages is null
     */
    public void setInputMessages(List<Msg> inputMessages) {
        this.inputMessages = Objects.requireNonNull(inputMessages, "inputMessages cannot be null");
    }
}
