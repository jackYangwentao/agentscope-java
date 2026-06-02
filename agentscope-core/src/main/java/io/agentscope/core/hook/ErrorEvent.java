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
import java.util.Objects;

/**
 * Agent 执行过程中发生错误时触发的事件。
 *
 * <p>此事件为通知型(只读) — 允许 Hook 记录错误或将错误上报到监控系统。
 * Hook 无法通过此事件修改或吞没错误。
 *
 * <p><b>不可修改:</b> 通知型事件
 *
 * <p>Event fired when an error occurs during agent execution.
 *
 * <p>This is a notification-only (read-only) event — hooks can log the error
 * or report it to monitoring systems. Hooks cannot modify or swallow the error
 * through this event.
 *
 * <p><b>Not Modifiable:</b> Notification event
 */
public final class ErrorEvent extends HookEvent {

    private final Throwable error;

    /**
     * ErrorEvent 的构造方法。
     *
     * @param agent Agent 实例(不能为 null)
     * @param error 发生的错误(不能为 null)
     * @throws NullPointerException 如果 agent 或 error 为 null
     *
     * <p>Constructor for ErrorEvent.
     *
     * @param agent The agent instance (must not be null)
     * @param error The error that occurred (must not be null)
     * @throws NullPointerException if agent or error is null
     */
    public ErrorEvent(Agent agent, Throwable error) {
        super(HookEventType.ERROR, agent);
        this.error = Objects.requireNonNull(error, "error cannot be null");
    }

    /**
     * 获取发生的错误。
     *
     * @return 抛出的异常
     *
     * <p>Get the error that occurred.
     *
     * @return The error
     */
    public Throwable getError() {
        return error;
    }
}
