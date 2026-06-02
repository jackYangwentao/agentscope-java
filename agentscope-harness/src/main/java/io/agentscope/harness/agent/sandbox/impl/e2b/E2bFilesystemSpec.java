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

import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/**
 * E2B 云沙箱的 {@link SandboxFilesystemSpec} 实现。
 * {@link SandboxFilesystemSpec} for E2B cloud sandboxes.
 */
public class E2bFilesystemSpec extends SandboxFilesystemSpec {

    /** 沙箱客户端实例。Sandbox client instance. */
    private SandboxClient<?> client;

    /** E2B 沙箱客户端选项。E2B sandbox client options. */
    private final E2bSandboxClientOptions options = new E2bSandboxClientOptions();

    /** 快照规范。Snapshot specification. */
    private SandboxSnapshotSpec snapshotSpec = new NoopSnapshotSpec();

    /** 默认工作空间规范。Default workspace specification. */
    private WorkspaceSpec defaultWorkspaceSpec = new WorkspaceSpec();

    /**
     * 设置沙箱客户端并返回当前实例（流式 API）。
     * Sets the sandbox client and returns this instance (fluent API).
     */
    public E2bFilesystemSpec client(SandboxClient<?> client) {
        this.client = client;
        return this;
    }

    /**
     * 设置 E2B API 密钥并返回当前实例。
     * Sets the E2B API key and returns this instance.
     */
    public E2bFilesystemSpec apiKey(String apiKey) {
        options.setApiKey(apiKey);
        return this;
    }

    /**
     * 设置 E2B API 基础 URL 并返回当前实例。
     * Sets the E2B API base URL and returns this instance.
     */
    public E2bFilesystemSpec apiBaseUrl(String apiBaseUrl) {
        options.setApiBaseUrl(apiBaseUrl);
        return this;
    }

    /**
     * 设置 E2B 域并返回当前实例。
     * Sets the E2B domain and returns this instance.
     */
    public E2bFilesystemSpec domain(String domain) {
        options.setDomain(domain);
        return this;
    }

    /**
     * 设置模板 ID 并返回当前实例。
     * Sets the template ID and returns this instance.
     */
    public E2bFilesystemSpec templateId(String templateId) {
        options.setTemplateId(templateId);
        return this;
    }

    /**
     * 设置工作空间根路径并返回当前实例。
     * Sets the workspace root path and returns this instance.
     */
    public E2bFilesystemSpec workspaceRoot(String workspaceRoot) {
        options.setWorkspaceRoot(workspaceRoot);
        return this;
    }

    /**
     * 设置沙箱超时秒数并返回当前实例。
     * Sets the sandbox timeout seconds and returns this instance.
     */
    public E2bFilesystemSpec sandboxTimeoutSeconds(int sandboxTimeoutSeconds) {
        options.setSandboxTimeoutSeconds(sandboxTimeoutSeconds);
        return this;
    }

    /**
     * 设置运行用户并返回当前实例。
     * Sets the run user and returns this instance.
     */
    public E2bFilesystemSpec runUser(String runUser) {
        options.setRunUser(runUser);
        return this;
    }

    /**
     * 设置持久化模式并返回当前实例。
     * Sets the persistence mode and returns this instance.
     */
    public E2bFilesystemSpec persistenceMode(E2bPersistenceMode persistenceMode) {
        options.setPersistenceMode(persistenceMode);
        return this;
    }

    /**
     * 设置连接超时秒数并返回当前实例。
     * Sets the connect timeout seconds and returns this instance.
     */
    public E2bFilesystemSpec connectTimeoutSeconds(int connectTimeoutSeconds) {
        options.setConnectTimeoutSeconds(connectTimeoutSeconds);
        return this;
    }

    /**
     * 设置读取超时秒数并返回当前实例。
     * Sets the read timeout seconds and returns this instance.
     */
    public E2bFilesystemSpec readTimeoutSeconds(int readTimeoutSeconds) {
        options.setReadTimeoutSeconds(readTimeoutSeconds);
        return this;
    }

    /**
     * 设置最大重试次数并返回当前实例。
     * Sets the maximum retries and returns this instance.
     */
    public E2bFilesystemSpec maxRetries(int maxRetries) {
        options.setMaxRetries(maxRetries);
        return this;
    }

    /**
     * 设置快照规范并返回当前实例。
     * Sets the snapshot specification and returns this instance.
     */
    public E2bFilesystemSpec snapshotSpec(SandboxSnapshotSpec snapshotSpec) {
        this.snapshotSpec = snapshotSpec;
        return this;
    }

    /**
     * 设置默认工作空间规范并返回当前实例。
     * Sets the default workspace specification and returns this instance.
     */
    public E2bFilesystemSpec workspaceSpec(WorkspaceSpec workspaceSpec) {
        this.defaultWorkspaceSpec = workspaceSpec;
        return this;
    }

    @Override
    protected SandboxClient<?> createClient() {
        return client != null ? client : options.createClient();
    }

    @Override
    protected SandboxClientOptions clientOptions() {
        return options;
    }

    @Override
    protected SandboxSnapshotSpec snapshotSpec() {
        return snapshotSpec;
    }

    @Override
    protected WorkspaceSpec workspaceSpec() {
        return defaultWorkspaceSpec;
    }
}
