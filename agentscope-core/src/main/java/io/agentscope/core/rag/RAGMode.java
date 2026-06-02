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
package io.agentscope.core.rag;

/**
 * RAG（检索增强生成）模式枚举。
 *
 * <p>定义知识检索如何与智能体集成：
 * <ul>
 *   <li><b>GENERIC</b>：通过钩子在每个推理步骤前自动检索并注入知识</li>
 *   <li><b>AGENTIC</b>：智能体通过工具主动决定何时检索知识</li>
 *   <li><b>NONE</b>：不启用 RAG 功能</li>
 * </ul>
 */
public enum RAGMode {
    /**
     * Generic mode: Knowledge is automatically retrieved and injected
     * before each reasoning step via Hook.
     *
     * <p>In this mode, the system automatically retrieves relevant knowledge
     * based on user queries and injects it into the prompt context.
     */
    GENERIC,

    /**
     * Agentic mode: Agent decides when to retrieve knowledge via Tool.
     *
     * <p>In this mode, the agent has a tool to retrieve knowledge and
     * actively decides when to use it based on the conversation context.
     */
    AGENTIC,

    /**
     * Disabled mode: No RAG functionality.
     *
     * <p>Knowledge retrieval is not enabled for this agent.
     */
    NONE
}
