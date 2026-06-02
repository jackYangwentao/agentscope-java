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

/**
 * AgentRun 沙箱的 NAS 挂载配置。
 * <p>
 * NAS 挂载在沙箱创建时配置，提供实例级别的持久化存储，可在沙箱重启后保留。
 * 使用 NAS 挂载作为 {@code workspaceRoot} 后端存储可获得"免费"持久化，
 * 无需管理 tar 快照。
 * <p>
 * NAS mount configuration for an AgentRun sandbox.
 * NAS mounts are configured at sandbox creation time and provide instance-level persistent
 * storage that survives sandbox restarts. Use a NAS mount as the {@code workspaceRoot} backing
 * store to get "free" persistence without managing tar snapshots.
 */
public class AgentRunNasMountConfig {

    /** NAS 服务器地址。NAS server address. */
    private String serverAddr;

    /** 沙箱内挂载目录。In-sandbox mount directory. */
    private String mountDir;

    /** NAS 文件系统上的远程路径。默认值 {@code "/"}。Remote path on the NAS filesystem. */
    private String remotePath = "/";

    /** 是否启用 TLS。默认值 {@code false}。Whether TLS is enabled. */
    private boolean enableTLS = false;

    /** 默认构造器。Default constructor. */
    public AgentRunNasMountConfig() {}

    /**
     * 返回 NAS 服务器地址（例如 {@code 12345-abc.cn-hangzhou.nas.aliyuncs.com}）。
     * Returns the NAS server address.
     *
     * @return NAS 服务器地址 / NAS server address
     */
    public String getServerAddr() {
        return serverAddr;
    }

    /**
     * 设置 NAS 服务器地址并返回当前实例（流式 API）。
     * Sets the NAS server address and returns this instance (fluent API).
     */
    public AgentRunNasMountConfig setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
        return this;
    }

    /**
     * 返回沙箱内挂载目录（必须以 {@code /home}、{@code /mnt} 或 {@code /data} 开头）。
     * Returns the in-sandbox mount directory.
     *
     * @return 绝对挂载路径 / absolute mount path
     */
    public String getMountDir() {
        return mountDir;
    }

    /**
     * 设置沙箱内挂载目录并返回当前实例。
     * Sets the in-sandbox mount directory and returns this instance.
     */
    public AgentRunNasMountConfig setMountDir(String mountDir) {
        this.mountDir = mountDir;
        return this;
    }

    /**
     * 返回 NAS 文件系统上的远程路径。
     * Returns the remote path on the NAS filesystem.
     *
     * @return 远程路径，默认为 {@code "/"} / remote path, defaults to {@code "/"}
     */
    public String getRemotePath() {
        return remotePath;
    }

    /**
     * 设置远程路径并返回当前实例。
     * Sets the remote path and returns this instance.
     */
    public AgentRunNasMountConfig setRemotePath(String remotePath) {
        this.remotePath = remotePath != null ? remotePath : "/";
        return this;
    }

    /**
     * 返回是否启用 TLS。
     * Returns whether TLS is enabled for the NAS connection.
     *
     * @return 启用 TLS 时返回 true / true when TLS is enabled
     */
    public boolean isEnableTLS() {
        return enableTLS;
    }

    /**
     * 设置是否启用 TLS 并返回当前实例。
     * Sets whether TLS is enabled and returns this instance.
     */
    public AgentRunNasMountConfig setEnableTLS(boolean enableTLS) {
        this.enableTLS = enableTLS;
        return this;
    }
}
