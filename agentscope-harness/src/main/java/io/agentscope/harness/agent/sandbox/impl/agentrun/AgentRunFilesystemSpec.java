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

import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/**
 * 阿里云 AgentRun 后端的 {@link SandboxFilesystemSpec} 实现。
 * {@link SandboxFilesystemSpec} for the Alibaba Cloud AgentRun backend.
 */
public class AgentRunFilesystemSpec extends SandboxFilesystemSpec {

    /** 沙箱客户端实例。Sandbox client instance. */
    private SandboxClient<?> client;

    /** AgentRun 沙箱客户端选项。AgentRun sandbox client options. */
    private final AgentRunSandboxClientOptions options = new AgentRunSandboxClientOptions();

    /** 快照规范。Snapshot specification. */
    private SandboxSnapshotSpec snapshotSpec = new NoopSnapshotSpec();

    /** 默认工作空间规范。Default workspace specification. */
    private WorkspaceSpec defaultWorkspaceSpec = new WorkspaceSpec();

    /**
     * 设置沙箱客户端并返回当前实例（流式 API）。
     * Sets the sandbox client and returns this instance (fluent API).
     */
    public AgentRunFilesystemSpec client(SandboxClient<?> client) {
        this.client = client;
        return this;
    }

    /**
     * 设置 API 密钥并返回当前实例。
     * Sets the API key and returns this instance.
     */
    public AgentRunFilesystemSpec apiKey(String apiKey) {
        options.setApiKey(apiKey);
        return this;
    }

    /**
     * 设置账户 ID 并返回当前实例。
     * Sets the account ID and returns this instance.
     */
    public AgentRunFilesystemSpec accountId(String accountId) {
        options.setAccountId(accountId);
        return this;
    }

    /**
     * 设置区域并返回当前实例。
     * Sets the region and returns this instance.
     */
    public AgentRunFilesystemSpec region(String region) {
        options.setRegion(region);
        return this;
    }

    /**
     * 设置数据面基础 URL 并返回当前实例。
     * Sets the data plane base URL and returns this instance.
     */
    public AgentRunFilesystemSpec dataPlaneBaseUrl(String url) {
        options.setDataPlaneBaseUrl(url);
        return this;
    }

    /**
     * 设置模板名称并返回当前实例。
     * Sets the template name and returns this instance.
     */
    public AgentRunFilesystemSpec templateName(String templateName) {
        options.setTemplateName(templateName);
        return this;
    }

    /**
     * 设置 MCP 服务器 URL 并返回当前实例。
     * Sets the MCP server URL and returns this instance.
     */
    public AgentRunFilesystemSpec mcpServerUrl(String mcpServerUrl) {
        options.setMcpServerUrl(mcpServerUrl);
        return this;
    }

    /**
     * 设置 MCP 端点并返回当前实例。
     * Sets the MCP endpoint and returns this instance.
     */
    public AgentRunFilesystemSpec mcpEndpoint(String mcpEndpoint) {
        options.setMcpEndpoint(mcpEndpoint);
        return this;
    }

    /**
     * 设置沙箱空闲超时秒数并返回当前实例。
     * Sets the sandbox idle timeout seconds and returns this instance.
     */
    public AgentRunFilesystemSpec sandboxIdleTimeoutSeconds(int seconds) {
        options.setSandboxIdleTimeoutSeconds(seconds);
        return this;
    }

    /**
     * 设置 NAS 配置并返回当前实例。
     * Sets the NAS config and returns this instance.
     */
    public AgentRunFilesystemSpec nasConfig(AgentRunNasMountConfig nas) {
        options.setNasConfig(nas);
        return this;
    }

    /**
     * 添加 OSS 挂载并返回当前实例。
     * Adds an OSS mount and returns this instance.
     */
    public AgentRunFilesystemSpec addOssMount(AgentRunOssMountConfig mount) {
        options.addOssMount(mount);
        return this;
    }

    /**
     * 设置工作空间根路径并返回当前实例。
     * Sets the workspace root path and returns this instance.
     */
    public AgentRunFilesystemSpec workspaceRoot(String workspaceRoot) {
        options.setWorkspaceRoot(workspaceRoot);
        return this;
    }

    /**
     * 设置连接超时秒数并返回当前实例。
     * Sets the connect timeout seconds and returns this instance.
     */
    public AgentRunFilesystemSpec connectTimeoutSeconds(int seconds) {
        options.setConnectTimeoutSeconds(seconds);
        return this;
    }

    /**
     * 设置读取超时秒数并返回当前实例。
     * Sets the read timeout seconds and returns this instance.
     */
    public AgentRunFilesystemSpec readTimeoutSeconds(int seconds) {
        options.setReadTimeoutSeconds(seconds);
        return this;
    }

    /**
     * 设置最大重试次数并返回当前实例。
     * Sets the max retries and returns this instance.
     */
    public AgentRunFilesystemSpec maxRetries(int maxRetries) {
        options.setMaxRetries(maxRetries);
        return this;
    }

    /**
     * 设置快照规范并返回当前实例。
     * Sets the snapshot specification and returns this instance.
     */
    public AgentRunFilesystemSpec snapshotSpec(SandboxSnapshotSpec snapshotSpec) {
        this.snapshotSpec = snapshotSpec;
        return this;
    }

    /**
     * 设置默认工作空间规范并返回当前实例。
     * Sets the default workspace specification and returns this instance.
     */
    public AgentRunFilesystemSpec workspaceSpec(WorkspaceSpec workspaceSpec) {
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
