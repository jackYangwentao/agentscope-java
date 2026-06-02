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

import io.agentscope.harness.agent.sandbox.SandboxState;

/**
 * AgentRun 后端沙箱的可序列化状态。
 * Serializable state for an AgentRun-backed sandbox.
 */
public class AgentRunSandboxState extends SandboxState {

    /** AgentRun 沙箱容器内的默认工作空间根路径。Default workspace root path inside an AgentRun sandbox container. */
    public static final String DEFAULT_WORKSPACE_ROOT = "/home/agentscope/workspace";

    /** 沙箱 ID。Sandbox ID. */
    private String sandboxId;

    /** 工作空间根路径。默认值 {@link #DEFAULT_WORKSPACE_ROOT}。Workspace root path. */
    private String workspaceRoot = DEFAULT_WORKSPACE_ROOT;

    /** 模板名称。Template name. */
    private String templateName;

    /** 账户 ID。Account ID. */
    private String accountId;

    /** 区域。Region. */
    private String region;

    /** MCP 服务器 URL。MCP server URL. */
    private String mcpServerUrl;

    /** SDK 是否拥有沙箱生命周期。默认值 {@code true}。Whether the SDK owns the sandbox lifecycle. */
    private boolean sandboxOwned = true;

    /** 工作空间是否位于 NAS 持久化挂载上。默认值 {@code false}。Whether workspace is on a NAS persistent mount. */
    private boolean workspaceOnNas = false;

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
     * 返回模板名称。
     * Returns the template name.
     */
    public String getTemplateName() {
        return templateName;
    }

    /**
     * 设置模板名称。
     * Sets the template name.
     */
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    /**
     * 返回账户 ID。
     * Returns the account ID.
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * 设置账户 ID。
     * Sets the account ID.
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /**
     * 返回区域。
     * Returns the region.
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置区域。
     * Sets the region.
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * 返回 MCP 服务器 URL。
     * Returns the MCP server URL.
     */
    public String getMcpServerUrl() {
        return mcpServerUrl;
    }

    /**
     * 设置 MCP 服务器 URL。
     * Sets the MCP server URL.
     */
    public void setMcpServerUrl(String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
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
     * 返回 {@link #workspaceRoot} 是否位于 NAS 或 OSS 持久化挂载上。
     * Returns whether {@link #workspaceRoot} lives on a NAS or OSS persistent mount.
     *
     * <p>当为 {@code true} 时，工作空间在沙箱删除后仍然存在，适配器跳过基于 tar 的持久化；
     * "恢复"通过使用相同的确定性 ID 和相同的挂载配置重新创建沙箱来实现。
     * When {@code true}, the workspace survives sandbox deletion and the adapter skips tar-based
     * persistence; "resume" is achieved by recreating the sandbox with the same deterministic id
     * and the same mount configuration.
     *
     * @return 工作空间根路径是否由持久化挂载支持
     *         true when the workspace root is backed by a persistent mount
     */
    public boolean isWorkspaceOnNas() {
        return workspaceOnNas;
    }

    /**
     * 设置工作空间是否位于 NAS 持久化挂载上。
     * Sets whether the workspace is on a NAS persistent mount.
     */
    public void setWorkspaceOnNas(boolean workspaceOnNas) {
        this.workspaceOnNas = workspaceOnNas;
    }
}
