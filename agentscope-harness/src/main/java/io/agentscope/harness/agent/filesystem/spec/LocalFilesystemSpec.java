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

import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystemWithShell;
import io.agentscope.harness.agent.store.NamespaceFactory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地文件系统模式（支持 Shell 执行）的配置规范。
 *
 * <p>此规范创建一个 {@link LocalFilesystemWithShell}，其根目录为 Agent 工作区，
 * Shell 直接在宿主机上以 {@code sh -c <command>} 形式运行。
 * 长期记忆（{@code MEMORY.md}、{@code memory/}）和会话日志存储在本地磁盘上。
 *
 * <p>适用于单进程部署（个人助手、CLI 工具、本地开发循环），
 * 不需要分布式共享，且 Agent 被信任可以在宿主机上执行 Shell 命令。
 *
 * <p>对于需要跨副本共享长期记忆的分布式部署，建议使用 {@link RemoteFilesystemSpec}
 * （无 Shell）或沙箱文件系统规范（通过沙箱执行 Shell）。
 */
public class LocalFilesystemSpec {

    private int executeTimeoutSeconds = LocalFilesystemWithShell.DEFAULT_EXECUTE_TIMEOUT;
    private int maxOutputBytes = 100_000;
    private final Map<String, String> env = new LinkedHashMap<>();
    private boolean inheritEnv = false;
    private boolean virtualMode = false;

    /**
     * Sets the default command execution timeout in seconds.
     *
     * @param seconds timeout (must be positive)
     * @return this spec
     */
    public LocalFilesystemSpec executeTimeoutSeconds(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + seconds);
        }
        this.executeTimeoutSeconds = seconds;
        return this;
    }

    /**
     * Sets the maximum number of output bytes captured from any single shell command.
     *
     * @param bytes byte cap (must be positive)
     * @return this spec
     */
    public LocalFilesystemSpec maxOutputBytes(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("maxOutputBytes must be positive, got " + bytes);
        }
        this.maxOutputBytes = bytes;
        return this;
    }

    /**
     * Adds an environment variable that will be set for every shell command.
     *
     * @param name variable name
     * @param value variable value
     * @return this spec
     */
    public LocalFilesystemSpec env(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("env name must not be blank");
        }
        this.env.put(name, value);
        return this;
    }

    /**
     * Controls whether the parent process environment is inherited by shell commands. When
     * {@code false} (default), only variables added via {@link #env(String, String)} are visible.
     *
     * @param inherit whether to inherit parent env
     * @return this spec
     */
    public LocalFilesystemSpec inheritEnv(boolean inherit) {
        this.inheritEnv = inherit;
        return this;
    }

    /**
     * Enables virtual-path mode: paths are anchored to the workspace root and traversal outside
     * is blocked.
     *
     * @param virtual whether to enable virtual mode
     * @return this spec
     */
    public LocalFilesystemSpec virtualMode(boolean virtual) {
        this.virtualMode = virtual;
        return this;
    }

    /**
     * Builds the effective filesystem rooted at {@code workspace}.
     *
     * @param workspace agent workspace root
     * @param localNamespaceFactory optional namespace factory for per-user/session folder scoping
     * @return a {@link LocalFilesystemWithShell} wired with the options in this spec
     */
    public AbstractFilesystem toFilesystem(Path workspace, NamespaceFactory localNamespaceFactory) {
        return new LocalFilesystemWithShell(
                workspace,
                virtualMode,
                executeTimeoutSeconds,
                maxOutputBytes,
                env.isEmpty() ? null : Map.copyOf(env),
                inheritEnv,
                localNamespaceFactory);
    }
}
