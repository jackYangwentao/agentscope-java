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
package io.agentscope.harness.agent.sandbox;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshot;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Sandbox} 的抽象基础实现，具有 4 分支工作空间启动逻辑。
 * Abstract base implementation of {@link Sandbox} with the 4-branch workspace start logic.
 *
 * <h2>4 分支启动逻辑</h2>
 * <pre>
 * 分支 A: workspaceRootReady=true  &amp; 工作空间目录存在   → 仅应用临时条目
 * 分支 B: workspaceRootReady=true  &amp; 工作空间目录缺失  → 从快照恢复 + 临时条目
 * 分支 C: workspaceRootReady=false &amp; 快照可恢复 → 从快照初始化 + 所有条目
 * 分支 D: workspaceRootReady=false &amp; 无可恢复快照 → 从完整工作空间规范全新初始化
 * </pre>
 *
 * <p>子类实现后端特定的操作：
 * Subclasses implement the backend-specific operations:
 * <ul>
 *   <li>{@link #doExec(RuntimeContext, String, int)} — 在工作空间中执行 shell 命令</li>
 *   <li>{@link #doPersistWorkspace()} — 创建工作空间的 tar 归档</li>
 *   <li>{@link #doHydrateWorkspace(InputStream)} — 将 tar 归档解压到工作空间</li>
 *   <li>{@link #doSetupWorkspace()} — 创建工作空间根目录</li>
 *   <li>{@link #doDestroyWorkspace()} — 删除工作空间根目录（关闭时）</li>
 *   <li>{@link #getWorkspaceRoot()} — 返回工作空间根路径字符串</li>
 * </ul>
 */
public abstract class AbstractBaseSandbox implements Sandbox {

    private static final Logger log = LoggerFactory.getLogger(AbstractBaseSandbox.class);

    /** 工作空间探测命令的默认超时时间（秒）。 */
    private static final int PROBE_TIMEOUT_SECONDS = 10;

    private final SandboxState state;
    private final WorkspaceSpecApplier workspaceSpecApplier;
    private final AtomicBoolean running = new AtomicBoolean(false);

    protected AbstractBaseSandbox(SandboxState state) {
        this.state = state;
        this.workspaceSpecApplier = new WorkspaceSpecApplier(state.getWorkspaceSpec().getRoot());
    }

    /**
     * 执行 4 分支工作空间启动逻辑。
     * Executes the 4-branch workspace start logic.
     *
     * @throws Exception 如果工作空间启动失败
     */
    @Override
    public void start() throws Exception {
        WorkspaceSpec spec = state.getWorkspaceSpec();
        SandboxSnapshot snapshot = state.getSnapshot();

        try {
            if (state.isWorkspaceRootReady()) {
                // Workspace was ready at last stop — check if it still exists
                boolean stillExists = probeWorkspaceRootForPreservedResume();
                if (stillExists) {
                    // Branch A: workspace preserved — only apply ephemeral entries
                    log.debug(
                            "[sandbox] Branch A: workspace preserved, applying ephemeral entries");
                    workspaceSpecApplier.applyWorkspaceSpec(spec, true);
                } else {
                    // Branch B: workspace was lost — restore from snapshot + ephemeral entries
                    log.debug("[sandbox] Branch B: workspace lost, restoring from snapshot");
                    if (snapshot != null && snapshot.isRestorable()) {
                        doSetupWorkspace();
                        try (InputStream archive = snapshot.restore()) {
                            doHydrateWorkspace(archive);
                        }
                        workspaceSpecApplier.applyWorkspaceSpec(spec, true);
                    } else {
                        // Degrade to Branch D: no usable snapshot
                        log.warn("[sandbox] Branch B degraded to D: snapshot not restorable");
                        doSetupWorkspace();
                        workspaceSpecApplier.applyWorkspaceSpec(spec, false);
                    }
                }
            } else {
                // Workspace was not ready at last stop
                if (snapshot != null && snapshot.isRestorable()) {
                    // Branch C: restore from snapshot + all spec entries
                    log.debug("[sandbox] Branch C: restoring from snapshot");
                    doSetupWorkspace();
                    try (InputStream archive = snapshot.restore()) {
                        doHydrateWorkspace(archive);
                    }
                    workspaceSpecApplier.applyWorkspaceSpec(spec, false);
                } else {
                    // Branch D: fresh initialization from full workspace spec
                    log.debug("[sandbox] Branch D: fresh workspace initialization");
                    doSetupWorkspace();
                    workspaceSpecApplier.applyWorkspaceSpec(spec, false);
                }
            }
            applyWorkspaceProjectionIfChanged(spec);
            state.setWorkspaceRootReady(true);
            running.set(true);
        } catch (Exception e) {
            state.setWorkspaceRootReady(false);
            throw new SandboxException.WorkspaceStartException(
                    java.nio.file.Path.of(state.getWorkspaceSpec().getRoot()), e);
        }
    }

    /**
     * 持久化工作空间快照并将工作空间根标记为就绪。
     * Persists the workspace snapshot and marks the workspace root as ready.
     *
     * @throws Exception 如果快照持久化失败
     */
    @Override
    public void stop() throws Exception {
        SandboxSnapshot snapshot = state.getSnapshot();
        if (snapshot != null && snapshot.isPersistenceEnabled()) {
            try (InputStream archive = doPersistWorkspace()) {
                snapshot.persist(archive);
            }
        }
        state.setWorkspaceRootReady(true);
        running.set(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>调用 {@link #stop()} 然后 {@link #shutdown()}。
     * 停止失败会被记录但不会阻止关闭。
     */
    @Override
    public void close() throws Exception {
        try {
            stop();
        } catch (Exception e) {
            log.warn("[sandbox] Failed to stop sandbox during close, continuing shutdown", e);
        }
        shutdown();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public SandboxState getState() {
        return state;
    }

    /**
     * 委托给 {@link #doExec(RuntimeContext, String, int)} 并使用回退超时。
     */
    @Override
    public ExecResult exec(RuntimeContext runtimeContext, String command, Integer timeoutSeconds)
            throws Exception {
        int timeout = timeoutSeconds != null ? timeoutSeconds : getDefaultExecTimeoutSeconds();
        return doExec(runtimeContext, command, timeout);
    }

    @Override
    public InputStream persistWorkspace() throws Exception {
        return doPersistWorkspace();
    }

    @Override
    public void hydrateWorkspace(InputStream archive) throws Exception {
        doHydrateWorkspace(archive);
    }

    /**
     * 使用后端执行探测工作空间根目录是否仍然存在。
     * Probes whether the workspace root directory still exists, using a backend exec.
     *
     * <p>使用 {@code test -d {workspaceRoot}} 命令和 {@value #PROBE_TIMEOUT_SECONDS} 秒超时。
     * 如果命令退出码为 0 则返回 {@code true}。
     *
     * @return 如果工作空间根存在则返回 true
     */
    protected boolean probeWorkspaceRootForPreservedResume() {
        try {
            ExecResult result =
                    doExec(null, "test -d " + getWorkspaceRoot(), PROBE_TIMEOUT_SECONDS);
            return result.ok();
        } catch (Exception e) {
            log.warn(
                    "[sandbox] Probe for workspace root failed, assuming lost: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 返回默认的命令执行超时时间（秒）。
     * Returns the default command execution timeout in seconds.
     *
     * @return 默认超时（120 秒）
     */
    protected int getDefaultExecTimeoutSeconds() {
        return 120;
    }

    /**
     * 在工作空间中执行 shell 命令。
     * Executes a shell command within the workspace.
     *
     * @param runtimeContext 每次调用的上下文；对于内部探测可能为 {@code null}
     * @param command shell 命令字符串
     * @param timeoutSeconds 最大执行时间
     * @return 执行结果
     * @throws Exception 如果执行失败
     */
    protected abstract ExecResult doExec(
            RuntimeContext runtimeContext, String command, int timeoutSeconds) throws Exception;

    /**
     * 创建当前工作空间内容的 tar 归档。
     * Creates a tar archive of the current workspace contents.
     *
     * @return tar 流的 {@link InputStream}；调用者必须关闭
     * @throws Exception 如果归档失败
     */
    protected abstract InputStream doPersistWorkspace() throws Exception;

    /**
     * 将 tar 归档解压到工作空间。
     * Extracts a tar archive into the workspace.
     *
     * @param archive 要解压的 tar 归档流
     * @throws Exception 如果解压失败
     */
    protected abstract void doHydrateWorkspace(InputStream archive) throws Exception;

    /**
     * 创建工作空间根目录。
     * Creates the workspace root directory.
     *
     * @throws Exception 如果目录创建失败
     */
    protected abstract void doSetupWorkspace() throws Exception;

    /**
     * 销毁工作空间根以及所有后端资源。
     * Destroys the workspace root and any backend resources.
     *
     * @throws Exception 如果清理失败
     */
    protected abstract void doDestroyWorkspace() throws Exception;

    /**
     * 返回工作空间根目录的绝对路径。
     * Returns the absolute path of the workspace root directory.
     *
     * @return 工作空间根路径字符串
     */
    protected abstract String getWorkspaceRoot();

    private void applyWorkspaceProjectionIfChanged(WorkspaceSpec spec) throws Exception {
        WorkspaceProjectionApplier.ProjectionPayload payload =
                WorkspaceProjectionApplier.build(spec);
        if (payload == null) {
            return;
        }
        if (Objects.equals(payload.hash(), state.getWorkspaceProjectionHash())) {
            log.debug("[sandbox] Workspace projection unchanged, skipping");
            return;
        }
        if (payload.fileCount() > 0) {
            try (InputStream archive = new ByteArrayInputStream(payload.tarBytes())) {
                doHydrateWorkspace(archive);
            }
        }
        state.setWorkspaceProjectionHash(payload.hash());
        log.debug(
                "[sandbox] Workspace projection applied: files={}, hash={}",
                payload.fileCount(),
                payload.hash());
    }
}
