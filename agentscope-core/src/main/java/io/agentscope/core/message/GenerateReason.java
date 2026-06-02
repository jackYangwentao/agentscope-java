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
package io.agentscope.core.message;

/**
 * 表示智能体生成消息的原因。
 *
 * <p>此枚举帮助用户了解消息返回时智能体执行的上下文和状态。
 * 不同的原因指示可能需要采取不同的后续操作。
 *
 * <p>使用示例：
 * <pre>{@code
 * Msg response = agent.call(userMsg).block();
 * switch (response.getGenerateReason()) {
 *     case MODEL_STOP -> System.out.println("任务完成");
 *     case TOOL_SUSPENDED -> handleSuspendedTools(response);
 *     case REASONING_STOP_REQUESTED -> handleHumanReview(response);
 *     // ...
 * }
 * }</pre>
 */
public enum GenerateReason {

    /** Model stopped normally, task completed. */
    MODEL_STOP,

    /** Model returned tool calls (internal tools, framework will continue execution). */
    TOOL_CALLS,

    /** Structured output completed. */
    STRUCTURED_OUTPUT,

    /** Tool execution was suspended, waiting for user to provide results. */
    TOOL_SUSPENDED,

    /** Reasoning phase was stopped by a Hook (PostReasoningEvent.stopAgent()). */
    REASONING_STOP_REQUESTED,

    /** Acting phase was stopped by a Hook (PostActingEvent.stopAgent()). */
    ACTING_STOP_REQUESTED,

    /** Agent was interrupted. */
    INTERRUPTED,

    /** Maximum iterations reached. */
    MAX_ITERATIONS
}
