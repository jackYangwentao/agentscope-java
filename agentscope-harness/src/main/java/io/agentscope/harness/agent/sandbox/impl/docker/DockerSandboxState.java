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
package io.agentscope.harness.agent.sandbox.impl.docker;

import io.agentscope.harness.agent.sandbox.SandboxState;
import java.util.ArrayList;
import java.util.List;

/**
 * Docker 后端 {@link io.agentscope.harness.agent.sandbox.Sandbox} 的可序列化状态。
 * Serializable state for a Docker-backed {@link io.agentscope.harness.agent.sandbox.Sandbox}.
 *
 * <p>每次调用后持久化，以支持透明的容器恢复。如果 {@link #containerId} 标识的容器在恢复时仍然
 * 存活，沙箱会重新连接。如果容器已停止，则重新启动。如果容器已被移除，则创建新容器并从快照
 * 恢复工作空间。
 */
public class DockerSandboxState extends SandboxState {

    /** 底层 Docker 容器的容器 ID。Docker container ID of the backing container. */
    private String containerId;

    /** 人类可读的容器名称（例如 {@code agentscope-sandbox-<sessionId>}）。Human-readable container name. */
    private String containerName;

    /** 用于创建此容器的 Docker 镜像。Docker image used to create this container. */
    private String image;

    /** 容器内的工作空间根路径。Workspace root path inside the container. */
    private String workspaceRoot;

    /**
     * SDK 是否拥有容器生命周期（创建/停止/移除）。
     * Whether the SDK owns the container lifecycle (create/stop/remove).
     * 当 {@code false} 时，容器由开发者注入，不会被移除。
     */
    private boolean containerOwned = true;

    /** 可选的内存限制（字节），用于在恢复时重建容器。Optional memory limit in bytes. */
    private Long memorySizeBytes;

    /** 可选的 CPU 数量限制，用于在恢复时重建容器。Optional CPU count limit. */
    private Long cpuCount;

    /** 暴露的端口号，用于在恢复时重建容器。Exposed port numbers for container recreation on resume. */
    private int[] exposedPorts = {};

    /** Docker 网络模式或网络名称，传递给 {@code docker run --network}。Docker network mode or network name. */
    private String network;

    /** 附加到 {@code docker run} 镜像名称之前的原始参数。Additional raw arguments appended to {@code docker run}. */
    private List<String> additionalRunArgs = new ArrayList<>();

    /**
     * 返回 Docker 容器 ID。
     * Returns the Docker container ID.
     *
     * @return 容器 ID，如果容器尚未创建则返回 {@code null}
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * 设置 Docker 容器 ID。
     * Sets the Docker container ID.
     *
     * @param containerId Docker 容器 ID
     */
    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    /**
     * 返回容器名称。
     * Returns the container name.
     *
     * @return 容器名称
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * 设置容器名称。
     * Sets the container name.
     *
     * @param containerName 容器名称
     */
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    /**
     * 返回此容器使用的 Docker 镜像。
     * Returns the Docker image used for this container.
     *
     * @return Docker 镜像
     */
    public String getImage() {
        return image;
    }

    /**
     * 设置 Docker 镜像。
     * Sets the Docker image.
     *
     * @param image Docker 镜像
     */
    public void setImage(String image) {
        this.image = image;
    }

    /**
     * 返回容器内的工作空间根路径。
     * Returns the workspace root path inside the container.
     *
     * @return 工作空间根路径
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 设置容器内的工作空间根路径。
     * Sets the workspace root path inside the container.
     *
     * @param workspaceRoot 容器内的绝对路径
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * 返回 SDK 是否拥有容器生命周期。
     * Returns whether the SDK owns the container lifecycle.
     *
     * @return {@code true} 如果 SDK 管理容器的创建和移除
     */
    public boolean isContainerOwned() {
        return containerOwned;
    }

    /**
     * 设置 SDK 是否拥有容器生命周期。
     * Sets whether the SDK owns the container lifecycle.
     *
     * @param containerOwned {@code true} 如果 SDK 应在关闭时停止并移除容器
     */
    public void setContainerOwned(boolean containerOwned) {
        this.containerOwned = containerOwned;
    }

    /**
     * 返回可选的内存限制（字节）。
     * Returns the optional memory limit in bytes.
     *
     * @return 内存限制或 {@code null}
     */
    public Long getMemorySizeBytes() {
        return memorySizeBytes;
    }

    /**
     * 设置内存限制（字节）。
     * Sets the memory limit in bytes.
     *
     * @param memorySizeBytes 内存限制
     */
    public void setMemorySizeBytes(Long memorySizeBytes) {
        this.memorySizeBytes = memorySizeBytes;
    }

    /**
     * 返回可选的 CPU 数量限制。
     * Returns the optional CPU count limit.
     *
     * @return CPU 数量或 {@code null}
     */
    public Long getCpuCount() {
        return cpuCount;
    }

    /**
     * 设置 CPU 数量限制。
     * Sets the CPU count limit.
     *
     * @param cpuCount CPU 数量
     */
    public void setCpuCount(Long cpuCount) {
        this.cpuCount = cpuCount;
    }

    /**
     * 返回暴露的端口号。
     * Returns the exposed port numbers.
     *
     * @return 端口号数组
     */
    public int[] getExposedPorts() {
        return exposedPorts;
    }

    /**
     * 设置暴露的端口号。
     * Sets the exposed port numbers.
     *
     * @param exposedPorts 端口号
     */
    public void setExposedPorts(int[] exposedPorts) {
        this.exposedPorts = exposedPorts != null ? exposedPorts : new int[0];
    }

    /**
     * 返回 Docker 网络模式或网络名称。
     * Returns the docker network mode or network name.
     *
     * @return Docker 网络值，未设置时返回 {@code null}
     */
    public String getNetwork() {
        return network;
    }

    /**
     * 设置 Docker 网络模式或网络名称。
     * Sets the docker network mode or network name.
     *
     * @param network Docker 网络值
     */
    public void setNetwork(String network) {
        if (network == null) {
            this.network = null;
            return;
        }
        String trimmed = network.trim();
        this.network = trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 返回附加到 {@code docker run} 的原始参数。
     * Returns additional raw arguments appended to {@code docker run}.
     *
     * @return 附加的 docker run 参数
     */
    public List<String> getAdditionalRunArgs() {
        return additionalRunArgs;
    }

    /**
     * 设置附加到 {@code docker run} 的原始参数。
     * Sets additional raw arguments appended to {@code docker run}.
     *
     * @param additionalRunArgs 附加的 docker run 参数
     */
    public void setAdditionalRunArgs(List<String> additionalRunArgs) {
        this.additionalRunArgs = new ArrayList<>();
        if (additionalRunArgs == null) {
            return;
        }
        for (String additionalRunArg : additionalRunArgs) {
            if (additionalRunArg != null && !additionalRunArg.isBlank()) {
                this.additionalRunArgs.add(additionalRunArg);
            }
        }
    }
}
