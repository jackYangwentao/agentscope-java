/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.core.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.tracing.TracerRegistry;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * AgentScope 框架中所有模型的抽象基类。
 *
 * <p>该类提供模型的通用功能，包括基本的模型调用和链路追踪。
 */
public abstract class ChatModelBase implements Model {

    /**
     * Stream chat completion responses.
     * The model internally handles message formatting using its configured formatter.
     *
     * <p>Tracing data will be captured once telemetry is enabled.
     *
     * @param messages AgentScope messages to send to the model
     * @param tools Optional list of tool schemas (null or empty if no tools)
     * @param options Optional generation options (null to use defaults)
     * @return Flux stream of chat responses
     */
    @Override
    public final Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return TracerRegistry.get()
                .callModel(
                        this, messages, tools, options, () -> doStream(messages, tools, options));
    }

    /**
     * Internal implementation for streaming chat completion responses.
     * Subclasses must implement their specific logic here.
     *
     * @param messages AgentScope messages to send to the model
     * @param tools Optional list of tool schemas (null or empty if no tools)
     * @param options Optional generation options (null to use defaults)
     * @return Flux stream of chat responses
     */
    protected abstract Flux<ChatResponse> doStream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options);
}
