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
import java.io.InputStream;

/**
 * 具有完全隔离工作空间的活跃沙箱。
 * An active sandbox with a fully isolated workspace.
 *
 * <p>生命周期：Lifecycle:
 * <ol>
 *   <li>通过 {@link SandboxClient#create}（新建）或 {@link SandboxClient#resume}（恢复）获取
 *   <li>调用 {@link #start()} — 初始化或恢复工作空间
 *   <li>使用 {@link #exec} 执行命令，{@link #persistWorkspace}/{@link #hydrateWorkspace} 进行归档操作
 *   <li>调用 {@link #stop()} — 持久化快照（不会销毁资源）
 *   <li>调用 {@link #shutdown()} — 销毁后端资源（临时目录、容器）
 *   <li>或使用 {@link #close()} 依次调用 stop + shutdown
 * </ol>
 *
 * <p>{@code stop()} 和 {@code shutdown()} 的区别至关重要：
 * The distinction between {@code stop()} and {@code shutdown()} is critical:
 * <ul>
 *   <li>{@code stop()}: 仅持久化快照 — 对自管理和用户管理的沙箱都安全</li>
 *   <li>{@code shutdown()}: 销毁后端资源 — 仅在自管理沙箱上调用</li>
 * </ul>
 */
public interface Sandbox extends AutoCloseable {

    void start() throws Exception;

    void stop() throws Exception;

    default void shutdown() throws Exception {
        // no-op by default
    }

    @Override
    void close() throws Exception;

    boolean isRunning();

    /**
     * 返回此沙箱的当前可序列化状态。
     * Returns the current serializable state of this sandbox.
     *
     * @return 状态（可能被生命周期方法修改）
     */
    SandboxState getState();

    /**
     * 在沙箱工作空间中运行 shell 命令。
     * Runs a shell command in the sandbox workspace.
     *
     * @param runtimeContext 每次调用的代理上下文（会话、用户、属性）；可为 {@code null}
     * @param command shell 命令
     * @param timeoutSeconds 最大等待时间；{@code null} 表示使用实现默认值
     */
    ExecResult exec(RuntimeContext runtimeContext, String command, Integer timeoutSeconds)
            throws Exception;

    InputStream persistWorkspace() throws Exception;

    void hydrateWorkspace(InputStream archive) throws Exception;
}
