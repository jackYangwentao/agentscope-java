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
package io.agentscope.harness.agent.sandbox.impl.daytona;

import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/**
 * Daytona 的 {@link SandboxFilesystemSpec} 实现。
 * {@link SandboxFilesystemSpec} for Daytona.
 */
public class DaytonaFilesystemSpec extends SandboxFilesystemSpec {

    /** 沙箱客户端实例。Sandbox client instance. */
    private SandboxClient<?> client;

    /** Daytona 沙箱客户端选项。Daytona sandbox client options. */
    private final DaytonaSandboxClientOptions options = new DaytonaSandboxClientOptions();

    /** 快照规范。Snapshot specification. */
    private SandboxSnapshotSpec snapshotSpec = new NoopSnapshotSpec();

    /** 默认工作空间规范。Default workspace specification. */
    private WorkspaceSpec defaultWorkspaceSpec = new WorkspaceSpec();

    /**
     * 设置沙箱客户端并返回当前实例（流式 API）。
     * Sets the sandbox client and returns this instance (fluent API).
     */
    public DaytonaFilesystemSpec client(SandboxClient<?> client) {
        this.client = client;
        return this;
    }

    /** 设置 API 密钥并返回当前实例。Sets the API key and returns this instance. */
    public DaytonaFilesystemSpec apiKey(String apiKey) {
        options.setApiKey(apiKey);
        return this;
    }

    /** 设置控制面基础 URL 并返回当前实例。Sets the control plane base URL and returns this instance. */
    public DaytonaFilesystemSpec controlPlaneBaseUrl(String url) {
        options.setControlPlaneBaseUrl(url);
        return this;
    }

    /** 设置工具箱基础 URL 并返回当前实例。Sets the toolbox base URL and returns this instance. */
    public DaytonaFilesystemSpec toolboxBaseUrl(String url) {
        options.setToolboxBaseUrl(url);
        return this;
    }

    /** 设置容器镜像并返回当前实例。Sets the container image and returns this instance. */
    public DaytonaFilesystemSpec image(String image) {
        options.setImage(image);
        return this;
    }

    /** 设置快照 ID 并返回当前实例。Sets the snapshot ID and returns this instance. */
    public DaytonaFilesystemSpec snapshotId(String snapshotId) {
        options.setSnapshotId(snapshotId);
        return this;
    }

    /** 设置 CPU 核心数并返回当前实例。Sets the CPU core count and returns this instance. */
    public DaytonaFilesystemSpec cpu(int cpu) {
        options.setCpu(cpu);
        return this;
    }

    /** 设置内存大小（GiB）并返回当前实例。Sets the memory (GiB) and returns this instance. */
    public DaytonaFilesystemSpec memory(int memoryGiB) {
        options.setMemory(memoryGiB);
        return this;
    }

    /** 设置磁盘大小（GiB）并返回当前实例。Sets the disk size (GiB) and returns this instance. */
    public DaytonaFilesystemSpec disk(int diskGiB) {
        options.setDisk(diskGiB);
        return this;
    }

    /** 设置工作空间根路径并返回当前实例。Sets the workspace root path and returns this instance. */
    public DaytonaFilesystemSpec workspaceRoot(String workspaceRoot) {
        options.setWorkspaceRoot(workspaceRoot);
        return this;
    }

    /** 设置快照规范并返回当前实例。Sets the snapshot specification and returns this instance. */
    public DaytonaFilesystemSpec snapshotSpec(SandboxSnapshotSpec snapshotSpec) {
        this.snapshotSpec = snapshotSpec;
        return this;
    }

    /** 设置默认工作空间规范并返回当前实例。Sets the default workspace specification and returns this instance. */
    public DaytonaFilesystemSpec workspaceSpec(WorkspaceSpec workspaceSpec) {
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
