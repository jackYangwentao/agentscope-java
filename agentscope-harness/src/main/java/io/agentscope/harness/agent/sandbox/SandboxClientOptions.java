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
package io.agentscope.harness.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;

/**
 * 沙箱客户端配置选项的基类。
 * Base class for sandbox client configuration options.
 *
 * <p>每个具体子类描述一个特定的沙箱后端（例如 Docker），
 * 并可以通过 {@link #createClient()} 自实例化对应的 {@link SandboxClient}。
 * 这允许调用者仅配置选项对象，依赖
 * {@link io.agentscope.harness.agent.HarnessAgent.Builder} 自动推导出客户端。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DockerSandboxClientOptions.class, name = "docker"),
})
public abstract class SandboxClientOptions {

    /**
     * 返回 JSON 序列化中使用的类型鉴别器。
     * Returns the type discriminator used in JSON serialization.
     *
     * @return 类型字符串（例如 "docker"）
     */
    public abstract String getType();

    /**
     * 创建与这些选项对应的 {@link SandboxClient} 实现。
     * Creates the {@link SandboxClient} implementation that corresponds to these options.
     *
     * <p>当未提供显式客户端时，由 {@link io.agentscope.harness.agent.HarnessAgent.Builder} 调用，
     * 因此调用者只需配置选项对象即可。
     *
     * @return 一个新的客户端实例，立即可用
     */
    public abstract SandboxClient<? extends SandboxClientOptions> createClient();
}
