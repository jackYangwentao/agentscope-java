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
package io.agentscope.core.pipeline;

import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;

/**
 * AgentScope 中管道执行的基础接口。
 *
 * 管道提供智能体和操作的编排能力，支持多种模式，
 * 如串行、并行（扇出）或自定义流程。
 *
 * @param <T> 管道结果的类型
 */
public interface Pipeline<T> {

    /**
     * Execute the pipeline with the given input message.
     *
     * @param input Input message to process through the pipeline
     * @return Mono containing the pipeline result
     */
    Mono<T> execute(Msg input);

    /**
     * Execute the pipeline with the given input message and structured output.
     *
     * @param input Input message to process through the pipeline
     * @param structuredOutputClass The class type for structured output
     * @return Mono containing the pipeline result with structured output
     */
    Mono<T> execute(Msg input, Class<?> structuredOutputClass);

    /**
     * Execute the pipeline with no input (for pipelines that don't need input).
     *
     * @return Mono containing the pipeline result
     */
    default Mono<T> execute() {
        return execute((Msg) null);
    }

    /**
     * Execute the pipeline with no input but with structured output.
     *
     * @param structuredOutputClass The class type for structured output
     * @return Mono containing the pipeline result with structured output
     */
    default Mono<T> execute(Class<?> structuredOutputClass) {
        return execute(null, structuredOutputClass);
    }

    /**
     * Get a description of this pipeline.
     *
     * @return Human-readable description of the pipeline
     */
    default String getDescription() {
        return getClass().getSimpleName();
    }
}
