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

import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import okhttp3.OkHttpClient;

/**
 * {@link DaytonaSandboxClient} 的选项配置。
 * Options for {@link DaytonaSandboxClient}.
 */
public class DaytonaSandboxClientOptions extends SandboxClientOptions {

    /** 自定义 OkHttp 客户端。Custom OkHttp client. */
    private OkHttpClient httpClient;

    /** API 密钥。API key. */
    private String apiKey;

    /** 控制面基础 URL。默认值 {@code "https://app.daytona.io/api"}。Control plane base URL. */
    private String controlPlaneBaseUrl = "https://app.daytona.io/api";

    /** 工具箱基础 URL。默认值 {@code "https://proxy.app.daytona.io"}。Toolbox base URL. */
    private String toolboxBaseUrl = "https://proxy.app.daytona.io";

    /** 容器镜像。默认值 {@code "ubuntu:22.04"}。Container image. */
    private String image = "ubuntu:22.04";

    /** 快照 ID（从快照创建时使用）。Snapshot ID (when creating from a snapshot). */
    private String snapshotId;

    /** CPU 核心数。默认值 {@code 1}。CPU core count. */
    private Integer cpu = 1;

    /** 内存大小（GiB）。默认值 {@code 1}。Memory in GiB. */
    private Integer memory = 1;

    /** 磁盘大小（GiB）。默认值 {@code 3}。Disk size in GiB. */
    private Integer disk = 3;

    /** 工作空间根路径。默认值 {@link DaytonaSandboxState#DEFAULT_WORKSPACE_ROOT}。Workspace root path. */
    private String workspaceRoot = DaytonaSandboxState.DEFAULT_WORKSPACE_ROOT;

    /** 连接超时秒数。默认值 {@code 30}。Connect timeout in seconds. */
    private int connectTimeoutSeconds = 30;

    /** 读取超时秒数。默认值 {@code 120}。Read timeout in seconds. */
    private int readTimeoutSeconds = 120;

    /** 最大重试次数。默认值 {@code 3}。Maximum retry count. */
    private int maxRetries = 3;

    @Override
    public String getType() {
        return "daytona";
    }

    @Override
    public SandboxClient<? extends SandboxClientOptions> createClient() {
        return new DaytonaSandboxClient(this, null);
    }

    /** 返回自定义 OkHttp 客户端。Returns the custom OkHttp client. */
    public OkHttpClient getHttpClient() { return httpClient; }

    /** 设置自定义 OkHttp 客户端。Sets the custom OkHttp client. */
    public void setHttpClient(OkHttpClient httpClient) { this.httpClient = httpClient; }

    /** 返回 API 密钥。Returns the API key. */
    public String getApiKey() { return apiKey; }

    /** 设置 API 密钥。Sets the API key. */
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    /** 返回控制面基础 URL。Returns the control plane base URL. */
    public String getControlPlaneBaseUrl() { return controlPlaneBaseUrl; }

    /** 设置控制面基础 URL。Sets the control plane base URL. */
    public void setControlPlaneBaseUrl(String controlPlaneBaseUrl) { this.controlPlaneBaseUrl = controlPlaneBaseUrl; }

    /** 返回工具箱基础 URL。Returns the toolbox base URL. */
    public String getToolboxBaseUrl() { return toolboxBaseUrl; }

    /** 设置工具箱基础 URL。Sets the toolbox base URL. */
    public void setToolboxBaseUrl(String toolboxBaseUrl) { this.toolboxBaseUrl = toolboxBaseUrl; }

    /** 返回容器镜像。Returns the container image. */
    public String getImage() { return image; }

    /** 设置容器镜像。Sets the container image. */
    public void setImage(String image) { this.image = image; }

    /** 返回快照 ID。Returns the snapshot ID. */
    public String getSnapshotId() { return snapshotId; }

    /** 设置快照 ID。Sets the snapshot ID. */
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    /** 返回 CPU 核心数。Returns the CPU core count. */
    public Integer getCpu() { return cpu; }

    /** 设置 CPU 核心数。Sets the CPU core count. */
    public void setCpu(Integer cpu) { this.cpu = cpu; }

    /** 返回内存大小（GiB）。Returns the memory size (GiB). */
    public Integer getMemory() { return memory; }

    /** 设置内存大小。Sets the memory size. */
    public void setMemory(Integer memory) { this.memory = memory; }

    /** 返回磁盘大小（GiB）。Returns the disk size (GiB). */
    public Integer getDisk() { return disk; }

    /** 设置磁盘大小。Sets the disk size. */
    public void setDisk(Integer disk) { this.disk = disk; }

    /** 返回工作空间根路径。Returns the workspace root path. */
    public String getWorkspaceRoot() { return workspaceRoot; }

    /** 设置工作空间根路径。Sets the workspace root path. */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot != null ? workspaceRoot : DaytonaSandboxState.DEFAULT_WORKSPACE_ROOT;
    }

    /** 返回连接超时秒数。Returns the connect timeout in seconds. */
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }

    /** 设置连接超时秒数。Sets the connect timeout in seconds. */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }

    /** 返回读取超时秒数。Returns the read timeout in seconds. */
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }

    /** 设置读取超时秒数。Sets the read timeout in seconds. */
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }

    /** 返回最大重试次数。Returns the maximum retry count. */
    public int getMaxRetries() { return maxRetries; }

    /** 设置最大重试次数。Sets the maximum retry count. */
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
