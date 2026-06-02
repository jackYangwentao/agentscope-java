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
package io.agentscope.harness.agent.sandbox.impl.e2b;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.json.HarnessSandboxJacksonModule;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * E2B 的 {@link SandboxClient} 实现。
 * {@link SandboxClient} for E2B.
 */
public class E2bSandboxClient implements SandboxClient<E2bSandboxClientOptions> {

    /** 日志记录器。Logger. */
    private static final Logger log = LoggerFactory.getLogger(E2bSandboxClient.class);

    /** 用于序列化/反序列化沙箱状态的 Jackson ObjectMapper。 */
    private final ObjectMapper objectMapper;

    /** 默认 E2B 沙箱客户端选项。Default E2B sandbox client options. */
    private final E2bSandboxClientOptions defaultOptions;

    /**
     * 使用默认选项和 ObjectMapper 构造 E2bSandboxClient 实例。
     * Constructs an E2bSandboxClient instance with default options and ObjectMapper.
     */
    public E2bSandboxClient() {
        this(new E2bSandboxClientOptions(), null);
    }

    /**
     * 使用给定默认选项和 ObjectMapper 构造 E2bSandboxClient 实例。
     * Constructs an E2bSandboxClient instance with the given default options and ObjectMapper.
     *
     * @param defaultOptions 默认选项 / default options
     * @param objectMapper   Jackson ObjectMapper，用于序列化状态
     *                       Jackson ObjectMapper for state serialization
     */
    public E2bSandboxClient(E2bSandboxClientOptions defaultOptions, ObjectMapper objectMapper) {
        this.defaultOptions =
                defaultOptions != null ? defaultOptions : new E2bSandboxClientOptions();
        this.objectMapper =
                objectMapper != null
                        ? objectMapper
                        : new ObjectMapper()
                                .findAndRegisterModules()
                                .registerModule(new HarnessSandboxJacksonModule())
                                .registerModule(new E2bHarnessSandboxJacksonModule());
    }

    /**
     * 创建一个新的 E2B 沙箱实例。
     * Creates a new E2B sandbox instance.
     *
     * @param workspaceSpec 工作空间规范 / workspace specification
     * @param snapshotSpec  快照规范 / snapshot specification
     * @param options       客户端选项 / client options
     * @return 新创建的 E2B 沙箱 / newly created E2B sandbox
     */
    @Override
    public Sandbox create(
            WorkspaceSpec workspaceSpec,
            SandboxSnapshotSpec snapshotSpec,
            E2bSandboxClientOptions options) {
        String sessionId = UUID.randomUUID().toString();
        E2bSandboxClientOptions merged = merge(options);

        E2bSandboxState state = new E2bSandboxState();
        state.setSessionId(sessionId);
        state.setWorkspaceSpec(workspaceSpec);
        state.setTemplateId(merged.getTemplateId());
        state.setWorkspaceRoot(merged.getWorkspaceRoot());
        state.setSandboxOwned(true);
        state.setWorkspaceRootReady(false);
        state.setPersistenceMode(merged.getPersistenceMode());
        state.setSandboxDomain(merged.getDomain());

        if (snapshotSpec != null) {
            state.setSnapshot(snapshotSpec.build(sessionId));
        }

        log.debug("[sandbox-e2b] Creating sandbox sessionId={}", sessionId);
        return new E2bSandbox(state, merged);
    }

    /**
     * 从已有状态恢复一个 E2B 沙箱。
     * Resumes an E2B sandbox from existing state.
     *
     * @param state 沙箱状态 / sandbox state
     * @return 恢复的 E2B 沙箱 / resumed E2B sandbox
     */
    @Override
    public Sandbox resume(SandboxState state) {
        if (!(state instanceof E2bSandboxState e2b)) {
            throw new IllegalArgumentException(
                    "Expected E2bSandboxState but got: " + state.getClass().getName());
        }
        return new E2bSandbox(e2b, merge(null));
    }

    @Override
    public void delete(Sandbox sandbox) {}

    /**
     * 将沙箱状态序列化为 JSON 字符串。
     * Serializes sandbox state to a JSON string.
     */
    @Override
    public String serializeState(SandboxState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new SandboxException.SandboxConfigurationException(
                    "Failed to serialize E2B sandbox state", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化沙箱状态。
     * Deserializes sandbox state from a JSON string.
     */
    @Override
    public SandboxState deserializeState(String json) {
        try {
            return objectMapper.readValue(json, SandboxState.class);
        } catch (Exception e) {
            throw new SandboxException.SandboxConfigurationException(
                    "Failed to deserialize E2B sandbox state", e);
        }
    }

    /**
     * 合并调用选项与默认选项（调用选项中的非空值覆盖默认值）。
     * Merges call options with default options (non-null call values override defaults).
     */
    private E2bSandboxClientOptions merge(E2bSandboxClientOptions call) {
        E2bSandboxClientOptions o = copy(defaultOptions);
        if (call == null) {
            return o;
        }
        if (call.getApiKey() != null) {
            o.setApiKey(call.getApiKey());
        }
        if (call.getApiBaseUrl() != null) {
            o.setApiBaseUrl(call.getApiBaseUrl());
        }
        if (call.getDomain() != null) {
            o.setDomain(call.getDomain());
        }
        if (call.getSandboxTimeoutSeconds() > 0) {
            o.setSandboxTimeoutSeconds(call.getSandboxTimeoutSeconds());
        }
        if (call.getRunUser() != null) {
            o.setRunUser(call.getRunUser());
        }
        if (call.getPersistenceMode() != null) {
            o.setPersistenceMode(call.getPersistenceMode());
        }
        if (call.getTemplateId() != null) {
            o.setTemplateId(call.getTemplateId());
        }
        if (call.getWorkspaceRoot() != null) {
            o.setWorkspaceRoot(call.getWorkspaceRoot());
        }
        if (call.getHttpClient() != null) {
            o.setHttpClient(call.getHttpClient());
        }
        o.setConnectTimeoutSeconds(call.getConnectTimeoutSeconds());
        o.setReadTimeoutSeconds(call.getReadTimeoutSeconds());
        o.setMaxRetries(call.getMaxRetries());
        return o;
    }

    /**
     * 深拷贝 E2B 沙箱客户端选项。
     * Deep copies E2B sandbox client options.
     */
    private static E2bSandboxClientOptions copy(E2bSandboxClientOptions src) {
        E2bSandboxClientOptions o = new E2bSandboxClientOptions();
        o.setApiKey(src.getApiKey());
        o.setApiBaseUrl(src.getApiBaseUrl());
        o.setDomain(src.getDomain());
        o.setTemplateId(src.getTemplateId());
        o.setWorkspaceRoot(src.getWorkspaceRoot());
        o.setSandboxTimeoutSeconds(src.getSandboxTimeoutSeconds());
        o.setRunUser(src.getRunUser());
        o.setPersistenceMode(src.getPersistenceMode());
        o.setHttpClient(src.getHttpClient());
        o.setConnectTimeoutSeconds(src.getConnectTimeoutSeconds());
        o.setReadTimeoutSeconds(src.getReadTimeoutSeconds());
        o.setMaxRetries(src.getMaxRetries());
        return o;
    }
}
