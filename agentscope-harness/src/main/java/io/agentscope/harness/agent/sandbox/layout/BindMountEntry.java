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
package io.agentscope.harness.agent.sandbox.layout;

/**
 * 声明一个主机目录（在 Kubernetes 上为文件）以绑定挂载方式挂载到沙箱工作区中，
 * 挂载位置为 {@link io.agentscope.harness.agent.sandbox.WorkspaceSpec#getRoot()} 下的映射键路径。
 * <p>
 * {@link io.agentscope.harness.agent.sandbox.WorkspaceSpec} 条目的 <strong>键</strong> 是工作区内的相对路径
 * （例如键 {@code cache} → {@code <workspaceRoot>/cache}）。
 * {@link #hostPath} 是主机（Docker）或 Kubernetes 节点（HostPath 卷）上的绝对路径。
 * <p>
 * <strong>后端支持：</strong>{@link io.agentscope.harness.agent.sandbox.impl.docker.DockerSandbox}
 * 传递 {@code -v host:container}；{@link io.agentscope.harness.agent.sandbox.impl.kubernetes}
 * 使用 HostPath 卷 + volumeMount。云沙箱（Daytona、E2B）在运行时忽略此条目
 * （参见日志）；在这些环境中请使用投影（projection）或特定于供应商的挂载。
 * <p>
 * <strong>安全说明：</strong>绑定挂载会将主机路径暴露给沙箱工作负载；
 * 只挂载您信任模型和工具读取或修改的路径。
 * <p>
 * Declares a host directory (or file on Kubernetes) bind-mounted into the sandbox workspace at
 * the map key path under {@link io.agentscope.harness.agent.sandbox.WorkspaceSpec#getRoot()}.
 *
 * <p>The {@link io.agentscope.harness.agent.sandbox.WorkspaceSpec} entry <strong>key</strong> is
 * the relative path inside the workspace (for example key {@code cache} →
 * {@code <workspaceRoot>/cache}). The {@link #hostPath} is an absolute path on the host (Docker)
 * or on the Kubernetes node (HostPath volume).
 *
 * <p><strong>Backends:</strong> {@link io.agentscope.harness.agent.sandbox.impl.docker.DockerSandbox}
 * passes {@code -v host:container}; {@link io.agentscope.harness.agent.sandbox.impl.kubernetes}
 * uses a HostPath volume + volumeMount. Cloud sandboxes (Daytona, E2B) ignore this entry at
 * runtime (see logs); use projection or provider-specific mounts there instead.
 *
 * <p><strong>Security:</strong> bind mounts expose host paths to sandbox workloads; only mount
 * paths you trust the model and tools to read or modify.
 */
public class BindMountEntry extends WorkspaceEntry {

    private String hostPath;
    private boolean readOnly;

    public String getHostPath() {
        return hostPath;
    }

    public void setHostPath(String hostPath) {
        this.hostPath = hostPath;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
