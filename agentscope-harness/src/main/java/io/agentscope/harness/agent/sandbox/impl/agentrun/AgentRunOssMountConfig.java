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
 * AgentRun 沙箱的 OSS 实例级挂载配置。
 * <p>
 * OSS 挂载使用 ossfs FUSE，每个沙箱实例独立应用。每个沙箱最多支持五个挂载。
 * 挂载目标目录必须在 {@code /home}、{@code /mnt} 或 {@code /data} 下，
 * 且存储桶必须为标准存储，与沙箱位于同一区域。
 * <p>
 * OSS instance-level mount configuration for an AgentRun sandbox.
 * OSS mounts use ossfs FUSE and apply per-sandbox instance. Up to five mounts per sandbox
 * are supported. The mount target directory must reside under {@code /home}, {@code /mnt} or
 * {@code /data}, and the bucket must be in standard storage in the same region as the sandbox.
 */
public class AgentRunOssMountConfig {

    /** OSS 存储桶名称。OSS bucket name. */
    private String bucketName;

    /** OSS 存储桶子路径。默认值 {@code "/"}。OSS bucket sub-path. */
    private String bucketPath = "/";

    /** OSS 端点（例如 {@code oss-cn-hangzhou-internal.aliyuncs.com}）。OSS endpoint. */
    private String endpoint;

    /** 沙箱内挂载目录。In-sandbox mount directory. */
    private String mountDir;

    /** 是否只读。默认值 {@code false}。Whether read-only. */
    private boolean readOnly = false;

    /** 默认构造器。Default constructor. */
    public AgentRunOssMountConfig() {}

    /**
     * 返回要挂载的 OSS 存储桶名称。
     * Returns the OSS bucket name to mount.
     *
     * @return 存储桶名称 / bucket name
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * 设置 OSS 存储桶名称并返回当前实例（流式 API）。
     * Sets the OSS bucket name and returns this instance (fluent API).
     */
    public AgentRunOssMountConfig setBucketName(String bucketName) {
        this.bucketName = bucketName;
        return this;
    }

    /**
     * 返回要挂载的 OSS 存储桶子路径。
     * Returns the OSS bucket sub-path to mount.
     *
     * @return 存储桶路径，默认为 {@code "/"} / bucket path, defaults to {@code "/"}
     */
    public String getBucketPath() {
        return bucketPath;
    }

    /**
     * 设置 OSS 存储桶子路径并返回当前实例。
     * Sets the OSS bucket sub-path and returns this instance.
     */
    public AgentRunOssMountConfig setBucketPath(String bucketPath) {
        this.bucketPath = bucketPath != null ? bucketPath : "/";
        return this;
    }

    /**
     * 返回 OSS 端点（例如 {@code oss-cn-hangzhou-internal.aliyuncs.com}）。
     * Returns the OSS endpoint.
     *
     * @return OSS 端点 / OSS endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置 OSS 端点并返回当前实例。
     * Sets the OSS endpoint and returns this instance.
     */
    public AgentRunOssMountConfig setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * 返回沙箱内挂载目录。
     * Returns the in-sandbox mount directory.
     *
     * @return 绝对挂载路径（必须以 {@code /home}、{@code /mnt} 或 {@code /data} 开头）
     *         absolute mount path
     */
    public String getMountDir() {
        return mountDir;
    }

    /**
     * 设置沙箱内挂载目录并返回当前实例。
     * Sets the in-sandbox mount directory and returns this instance.
     */
    public AgentRunOssMountConfig setMountDir(String mountDir) {
        this.mountDir = mountDir;
        return this;
    }

    /**
     * 返回挂载是否为只读。
     * Returns whether the mount is read-only.
     *
     * @return 只读时返回 true / true when read-only
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 设置挂载是否为只读并返回当前实例。
     * Sets whether the mount is read-only and returns this instance.
     */
    public AgentRunOssMountConfig setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }
}
