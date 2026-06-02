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
package io.agentscope.harness.agent.sandbox.impl.agentrun;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * 在 {@code agentrun} 类型 ID 下注册 {@link AgentRunSandboxState} 的 Jackson 模块。
 * Jackson module registering {@link AgentRunSandboxState} under the {@code agentrun} type id.
 */
public final class AgentRunHarnessSandboxJacksonModule extends SimpleModule {

    /**
     * 构造一个注册了 AgentRunSandboxState 类型的 Jackson 模块。
     * Constructs a Jackson module with AgentRunSandboxState type registration.
     */
    public AgentRunHarnessSandboxJacksonModule() {
        super("harness-sandbox-agentrun");
        registerSubtypes(new NamedType(AgentRunSandboxState.class, "agentrun"));
    }
}
