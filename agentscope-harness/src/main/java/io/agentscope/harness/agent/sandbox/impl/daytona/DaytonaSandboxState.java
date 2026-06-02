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

import io.agentscope.harness.agent.sandbox.SandboxState;

/**
 * Daytona 后端沙箱的可序列化状态。
 * Serializable state for a Daytona-backed sandbox.
 */
public class DaytonaSandboxState extends SandboxState {

    /** Daytona 沙箱容器的默认工作空间根路径。Default workspace root path for Daytona sandbox containers. */
    public static final String DEFAULT_WORKSPACE_ROOT = "/home/daytona/workspace";

    /** 沙箱 ID。Sandbox ID. */
    private String sandboxId;

    /** 工作空间根路径。默认值 {@link #DEFAULT_WORKSPACE_ROOT}。Workspace root path. */
    private String workspaceRoot = DEFAULT_WORKSPACE_ROOT;

    /** SDK 是否拥有沙箱生命周期。默认值 {@code true}。Whether the SDK owns the sandbox lifecycle. */
    private boolean sandboxOwned = true;

    /** 容器镜像。默认值 {@code "ubuntu:22.04"}。Container image. */
    private String image = "ubuntu:22.04";

    /** 快照 ID。Snapshot ID. */
    private String snapshotId;

    /**
     * 返回沙箱 ID。
     * Returns the sandbox ID.
     */
    public String getSandboxId() {
        return sandboxId;
    }

    /**
     * 设置沙箱 ID。
     * Sets the sandbox ID.
     */
    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    /**
     * 返回工作空间根路径。
     * Returns the workspace root path.
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 设置工作空间根路径。
     * Sets the workspace root path.
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot != null ? workspaceRoot : DEFAULT_WORKSPACE_ROOT;
    }

    /**
     * 返回 SDK 是否拥有沙箱生命周期。
     * Returns whether the SDK owns the sandbox lifecycle.
     */
    public boolean isSandboxOwned() {
        return sandboxOwned;
    }

    /**
     * 设置 SDK 是否拥有沙箱生命周期。
     * Sets whether the SDK owns the sandbox lifecycle.
     */
    public void setSandboxOwned(boolean sandboxOwned) {
        this.sandboxOwned = sandboxOwned;
    }

    /**
     * 返回容器镜像。
     * Returns the container image.
     */
    public String getImage() {
        return image;
    }

    /**
     * 设置容器镜像。
     * Sets the container image.
     */
    public void setImage(String image) {
        this.image = image;
    }

    /**
     * 返回快照 ID。
     * Returns the snapshot ID.
     */
    public String getSnapshotId() {
        return snapshotId;
    }

    /**
     * 设置快照 ID。
     * Sets the snapshot ID.
     */
    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }
}
