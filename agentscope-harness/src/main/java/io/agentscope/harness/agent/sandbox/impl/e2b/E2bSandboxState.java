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

import io.agentscope.harness.agent.sandbox.SandboxState;

/**
 * E2B 后端沙箱的可序列化状态。
 * Serializable state for an E2B-backed sandbox.
 */
public class E2bSandboxState extends SandboxState {

    /** E2B 沙箱 ID。E2B sandbox ID. */
    private String sandboxId;

    /** E2B 模板 ID（从快照创建时可用作快照 ID）。默认值 {@code "base"}。E2B template id (or snapshot id when creating from a snapshot). */
    private String templateId = "base";

    /** 沙箱的 E2B 域。E2B domain for the sandbox. */
    private String sandboxDomain;

    /** envd 进程的访问令牌。Access token for the envd process. */
    private String envdAccessToken;

    /** envd 版本。默认值 {@code "0.1.5"}。Envd version. */
    private String envdVersion = "0.1.5";

    /** 容器内的工作空间根路径。默认值 {@code "/home/user"}。Workspace root path inside the container. */
    private String workspaceRoot = "/home/user";

    /** SDK 是否拥有沙箱生命周期。默认值 {@code true}。Whether the SDK owns the sandbox lifecycle. */
    private boolean sandboxOwned = true;

    /** 持久化模式。默认值 {@link E2bPersistenceMode#TAR}。Persistence mode. */
    private E2bPersistenceMode persistenceMode = E2bPersistenceMode.TAR;

    /**
     * 返回 E2B 沙箱 ID。
     * Returns the E2B sandbox ID.
     */
    public String getSandboxId() {
        return sandboxId;
    }

    /**
     * 设置 E2B 沙箱 ID。
     * Sets the E2B sandbox ID.
     */
    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    /**
     * 返回 E2B 模板 ID。
     * Returns the E2B template ID.
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * 设置 E2B 模板 ID。
     * Sets the E2B template ID.
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * 返回沙箱的 E2B 域。
     * Returns the E2B domain for the sandbox.
     */
    public String getSandboxDomain() {
        return sandboxDomain;
    }

    /**
     * 设置沙箱的 E2B 域。
     * Sets the E2B domain for the sandbox.
     */
    public void setSandboxDomain(String sandboxDomain) {
        this.sandboxDomain = sandboxDomain;
    }

    /**
     * 返回 envd 进程的访问令牌。
     * Returns the access token for the envd process.
     */
    public String getEnvdAccessToken() {
        return envdAccessToken;
    }

    /**
     * 设置 envd 进程的访问令牌。
     * Sets the access token for the envd process.
     */
    public void setEnvdAccessToken(String envdAccessToken) {
        this.envdAccessToken = envdAccessToken;
    }

    /**
     * 返回 envd 版本。
     * Returns the envd version.
     */
    public String getEnvdVersion() {
        return envdVersion;
    }

    /**
     * 设置 envd 版本。
     * Sets the envd version.
     */
    public void setEnvdVersion(String envdVersion) {
        this.envdVersion = envdVersion;
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
        this.workspaceRoot = workspaceRoot;
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
     * 返回持久化模式。
     * Returns the persistence mode.
     */
    public E2bPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    /**
     * 设置持久化模式。
     * Sets the persistence mode.
     */
    public void setPersistenceMode(E2bPersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode != null ? persistenceMode : E2bPersistenceMode.TAR;
    }
}
