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
package io.agentscope.harness.agent.sandbox.impl.kubernetes;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.AbstractBaseSandbox;
import io.agentscope.harness.agent.sandbox.ExecResult;
import java.io.InputStream;

/**
 * 基于 Kubernetes Pod 的 {@link io.agentscope.harness.agent.sandbox.Sandbox} 实现。
 * <p>
 * {@link io.agentscope.harness.agent.sandbox.Sandbox} backed by a Kubernetes Pod.
 */
public class KubernetesSandbox extends AbstractBaseSandbox {

    private final KubernetesSandboxState k8sState;
    private final Fabric8KubernetesPodRuntime runtime;

    public KubernetesSandbox(KubernetesSandboxState state, Fabric8KubernetesPodRuntime runtime) {
        super(state);
        this.k8sState = state;
        this.runtime = runtime;
    }

    /**
     * 启动沙箱，确保 Pod 就绪。
     * <p>
     * Start the sandbox, ensuring the Pod is ready.
     */
    @Override
    public void start() throws Exception {
        runtime.ensurePodReady(k8sState);
        super.start();
    }

    /**
     * 关闭沙箱，释放受管的 Pod 资源。
     * <p>
     * Shutdown the sandbox, releasing owned Pod resources.
     */
    @Override
    public void shutdown() throws Exception {
        if (!k8sState.isPodOwned()) {
            return;
        }
        runtime.deletePodIfOwned(k8sState);
    }

    /**
     * 在 Pod 中执行命令。
     * <p>
     * Execute a command in the Pod.
     */
    @Override
    protected ExecResult doExec(RuntimeContext runtimeContext, String command, int timeoutSeconds)
            throws Exception {
        return runtime.exec(k8sState, command, timeoutSeconds);
    }

    /**
     * 将工作区打包为 tar 归档流。
     * <p>
     * Package the workspace into a tar archive stream.
     */
    @Override
    protected InputStream doPersistWorkspace() throws Exception {
        return runtime.tarWorkspaceOut(k8sState);
    }

    /**
     * 从 tar 归档流恢复工作区。
     * <p>
     * Hydrate the workspace from a tar archive stream.
     */
    @Override
    protected void doHydrateWorkspace(InputStream archive) throws Exception {
        runtime.tarWorkspaceIn(k8sState, archive);
    }

    /**
     * 创建工作区根目录。
     * <p>
     * Create the workspace root directory.
     */
    @Override
    protected void doSetupWorkspace() throws Exception {
        runtime.mkdir(k8sState, k8sState.getWorkspaceRoot());
    }

    /**
     * 销毁工作区，清理文件。
     * <p>
     * Destroy the workspace, cleaning up files.
     */
    @Override
    protected void doDestroyWorkspace() throws Exception {
        runtime.rmRf(k8sState, k8sState.getWorkspaceRoot());
    }

    /**
     * 获取工作区根路径。
     * <p>
     * Get the workspace root path.
     */
    @Override
    protected String getWorkspaceRoot() {
        return k8sState.getWorkspaceRoot();
    }
}
