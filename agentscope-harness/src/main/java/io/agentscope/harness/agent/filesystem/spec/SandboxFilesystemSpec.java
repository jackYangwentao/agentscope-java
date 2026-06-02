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
package io.agentscope.harness.agent.filesystem.spec;

import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.SandboxContext;
import io.agentscope.harness.agent.sandbox.SandboxExecutionGuard;
import io.agentscope.harness.agent.sandbox.SandboxStateStore;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.layout.WorkspaceEntry;
import io.agentscope.harness.agent.sandbox.layout.WorkspaceProjectionEntry;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 声明式沙箱文件系统配置。
 *
 * <p>与 {@code AbstractFilesystem} 不同，此类型不是运行时文件系统实现。
 * 它仅描述如何在构建时创建沙箱支持的文件系统。
 */
public abstract class SandboxFilesystemSpec {

    private static final List<String> DEFAULT_WORKSPACE_PROJECTION_ROOTS =
            List.of("AGENTS.md", "skills", "subagents", "knowledge");

    private IsolationScope isolationScope;
    private SandboxSnapshotSpec snapshotSpecOverride;
    private SandboxStateStore sandboxStateStore;
    private SandboxExecutionGuard executionGuard;
    private boolean workspaceProjectionEnabled = true;
    private List<String> workspaceProjectionRoots = DEFAULT_WORKSPACE_PROJECTION_ROOTS;

    protected abstract SandboxClient<?> createClient();

    protected abstract SandboxClientOptions clientOptions();

    protected abstract SandboxSnapshotSpec snapshotSpec();

    protected abstract WorkspaceSpec workspaceSpec();

    public SandboxFilesystemSpec isolationScope(IsolationScope scope) {
        this.isolationScope = scope;
        return this;
    }

    public IsolationScope getIsolationScope() {
        return isolationScope;
    }

    public SandboxFilesystemSpec snapshotSpec(SandboxSnapshotSpec snapshotSpec) {
        this.snapshotSpecOverride = snapshotSpec;
        return this;
    }

    public SandboxSnapshotSpec getSnapshotSpecOverride() {
        return snapshotSpecOverride;
    }

    /**
     * 覆盖用于跨调用持久化和恢复沙箱元数据的 {@link SandboxStateStore}。
     * 当为 {@code null}（默认）时，{@link io.agentscope.harness.agent.HarnessAgent} 在构建时使用
     * 有效的 {@link io.agentscope.core.session.Session} 和代理 ID 的
     * {@link io.agentscope.harness.agent.sandbox.SessionSandboxStateStore}。
     *
     * @param sandboxStateStore 自定义存储，或 {@code null} 使用默认的基于会话的存储
     * @return 此规范
     */
    public SandboxFilesystemSpec sandboxStateStore(SandboxStateStore sandboxStateStore) {
        this.sandboxStateStore = sandboxStateStore;
        return this;
    }

    public SandboxStateStore getSandboxStateStore() {
        return sandboxStateStore;
    }

    /**
     * 设置 {@link SandboxExecutionGuard}，用于在同一隔离槽上序列化并发执行。
     *
     * <p>仅与 {@link io.agentscope.harness.agent.IsolationScope#AGENT} 和
     * {@link io.agentscope.harness.agent.IsolationScope#GLOBAL} 范围相关，
     * 在这些范围中多个调用方可能会竞争同一持久状态。当为 {@code null}（默认）时，
     * 不应用守卫并保留现有的无锁行为。
     *
     * @param executionGuard 应用的守卫，或 {@code null} 表示无守卫
     * @return 此规范
     */
    public SandboxFilesystemSpec executionGuard(SandboxExecutionGuard executionGuard) {
        this.executionGuard = executionGuard;
        return this;
    }

    public SandboxExecutionGuard getExecutionGuard() {
        return executionGuard;
    }

    public SandboxFilesystemSpec workspaceProjectionEnabled(boolean enabled) {
        this.workspaceProjectionEnabled = enabled;
        return this;
    }

    public SandboxFilesystemSpec workspaceProjectionRoots(List<String> includeRoots) {
        this.workspaceProjectionRoots =
                includeRoots != null
                        ? List.copyOf(includeRoots)
                        : DEFAULT_WORKSPACE_PROJECTION_ROOTS;
        return this;
    }

    public final SandboxContext toSandboxContext(Path hostWorkspaceRoot) {
        SandboxClient<?> client =
                Objects.requireNonNull(createClient(), "sandbox client is required");
        WorkspaceSpec withProjection = buildWorkspaceSpecWithProjection(hostWorkspaceRoot);
        return SandboxContext.builder()
                .client(client)
                .clientOptions(clientOptions())
                .snapshotSpec(snapshotSpecOverride != null ? snapshotSpecOverride : snapshotSpec())
                .workspaceSpec(withProjection)
                .isolationScope(isolationScope)
                .build();
    }

    public final SandboxContext toSandboxContext() {
        return toSandboxContext(null);
    }

    private WorkspaceSpec buildWorkspaceSpecWithProjection(Path hostWorkspaceRoot) {
        WorkspaceSpec base = workspaceSpec();
        WorkspaceSpec effective = base != null ? base.copy() : new WorkspaceSpec();
        if (!workspaceProjectionEnabled || hostWorkspaceRoot == null) {
            return effective;
        }
        WorkspaceProjectionEntry projection = new WorkspaceProjectionEntry();
        projection.setSourceRoot(hostWorkspaceRoot.toAbsolutePath().normalize().toString());
        projection.setIncludeRoots(workspaceProjectionRoots);

        Map<String, WorkspaceEntry> entries = new LinkedHashMap<>(effective.getEntries());
        entries.put("__workspace_projection__", projection);
        effective.setEntries(entries);
        return effective;
    }
}
