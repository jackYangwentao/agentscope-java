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

/**
 * 所有 Hook 事件类型的枚举。
 *
 * <p>这些事件在 Agent 执行的不同阶段触发,可通过实现 {@link Hook} 接口来拦截。
 *
 * <p>Enum representing all hook event types.
 *
 * <p>These events are fired at different stages of agent execution and can be
 * intercepted by implementing the {@link Hook} interface.
 *
 * @see Hook
 * @see HookEvent
 */
public enum HookEventType {
    /** Agent 开始处理前 */
    PRE_CALL,

    /** Agent 完成处理后 */
    POST_CALL,

    /** LLM 推理前 */
    PRE_REASONING,

    /** LLM 推理完成后 */
    POST_REASONING,

    /** LLM 推理流式传输期间 */
    REASONING_CHUNK,

    /** 工具执行前 */
    PRE_ACTING,

    /** 工具执行完成后 */
    POST_ACTING,

    /** 工具执行流式传输期间 */
    ACTING_CHUNK,

    /** 摘要生成前(达到最大迭代次数时) */
    PRE_SUMMARY,

    /** 摘要生成完成后 */
    POST_SUMMARY,

    /** 摘要流式传输期间 */
    SUMMARY_CHUNK,

    /** 发生错误时 */
    ERROR
}
