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
package io.agentscope.harness.agent.filesystem.local;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.filesystem.model.ExecuteResponse;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import io.agentscope.harness.agent.store.NamespaceFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 具有无限制本地 shell 命令执行能力的文件系统。
 *
 * <p>该实现扩展了 {@link LocalFilesystem}，增加了 shell 命令执行功能。
 * 命令直接在宿主机上执行，没有任何沙箱、进程隔离或安全限制。
 *
 * <p><b>警告：</b>此实现授予了代理对本地机器的 BOTH 直接文件系统访问和不受限制的 shell 执行权限。
 * 请极度谨慎使用，仅在适当的环境中使用（本地开发、具有适当秘密管理的 CI/CD）。
 */
public class LocalFilesystemWithShell extends LocalFilesystem implements AbstractSandboxFilesystem {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemWithShell.class);

    /** Shell 命令执行的默认超时时间（秒）。 */
    public static final int DEFAULT_EXECUTE_TIMEOUT = 120;

    private final String sandboxId;
    private final int defaultTimeout;
    private final int maxOutputBytes;
    private final Map<String, String> env;

    /**
     * 使用默认设置创建文件系统。
     *
     * @param rootDir 文件系统和 shell 操作的工作目录
     */
    public LocalFilesystemWithShell(Path rootDir) {
        this(rootDir, false, DEFAULT_EXECUTE_TIMEOUT, 100_000, null, false, null);
    }

    /**
     * 与 {@link #LocalFilesystemWithShell(Path)} 相同，但使用路径字符串；
     * 关于 {@code null} / 空白规则，请参见 {@link LocalFilesystem#LocalFilesystem(String)}。
     */
    public LocalFilesystemWithShell(String rootDir) {
        this(
                LocalFilesystem.rootDirFromString(rootDir),
                false,
                DEFAULT_EXECUTE_TIMEOUT,
                100_000,
                null,
                false,
                null);
    }

    /**
     * 使用默认设置和命名空间支持创建文件系统。
     *
     * @param rootDir 文件系统和 shell 操作的工作目录
     * @param namespaceFactory 可选的路径作用域命名空间工厂（{@code null} 表示不使用）
     */
    public LocalFilesystemWithShell(Path rootDir, NamespaceFactory namespaceFactory) {
        this(rootDir, false, DEFAULT_EXECUTE_TIMEOUT, 100_000, null, false, namespaceFactory);
    }

    /**
     * 与 {@link #LocalFilesystemWithShell(Path, NamespaceFactory)} 相同，但使用路径字符串；
     * 关于 {@code null} / 空白规则，请参见 {@link LocalFilesystem#LocalFilesystem(String)}。
     */
    public LocalFilesystemWithShell(String rootDir, NamespaceFactory namespaceFactory) {
        this(
                LocalFilesystem.rootDirFromString(rootDir),
                false,
                DEFAULT_EXECUTE_TIMEOUT,
                100_000,
                null,
                false,
                namespaceFactory);
    }

    /**
     * 使用完整配置创建文件系统。
     *
     * @param rootDir 文件系统和 shell 操作的工作目录
     * @param virtualMode 启用文件系统操作的虚拟路径模式
     * @param timeout shell 命令执行的默认最大超时时间（秒）
     * @param maxOutputBytes 从命令输出中捕获的最大字节数
     * @param env shell 命令的环境变量（{@code null} 表示空）
     * @param inheritEnv 是否继承父进程的环境变量
     */
    public LocalFilesystemWithShell(
            Path rootDir,
            boolean virtualMode,
            int timeout,
            int maxOutputBytes,
            Map<String, String> env,
            boolean inheritEnv) {
        this(rootDir, virtualMode, timeout, maxOutputBytes, env, inheritEnv, null);
    }

    /**
     * 与 {@link #LocalFilesystemWithShell(Path, boolean, int, int, Map, boolean)} 相同，但使用路径字符串；
     * 关于 {@code null} / 空白规则，请参见 {@link LocalFilesystem#LocalFilesystem(String)}。
     */
    public LocalFilesystemWithShell(
            String rootDir,
            boolean virtualMode,
            int timeout,
            int maxOutputBytes,
            Map<String, String> env,
            boolean inheritEnv) {
        this(
                LocalFilesystem.rootDirFromString(rootDir),
                virtualMode,
                timeout,
                maxOutputBytes,
                env,
                inheritEnv,
                null);
    }

    /**
     * 使用完整配置和命名空间支持创建文件系统。
     *
     * @param rootDir 文件系统和 shell 操作的工作目录
     * @param virtualMode 启用文件系统操作的虚拟路径模式
     * @param timeout shell 命令执行的默认最大超时时间（秒）
     * @param maxOutputBytes 从命令输出中捕获的最大字节数
     * @param env shell 命令的环境变量（{@code null} 表示空）
     * @param inheritEnv 是否继承父进程的环境变量
     * @param namespaceFactory 可选的路径作用域命名空间工厂（{@code null} 表示不使用）
     */
    public LocalFilesystemWithShell(
            Path rootDir,
            boolean virtualMode,
            int timeout,
            int maxOutputBytes,
            Map<String, String> env,
            boolean inheritEnv,
            NamespaceFactory namespaceFactory) {
        super(rootDir, virtualMode, 10, namespaceFactory);

        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + timeout);
        }

        this.defaultTimeout = timeout;
        this.maxOutputBytes = maxOutputBytes;
        this.sandboxId = "local-" + UUID.randomUUID().toString().substring(0, 8);

        if (inheritEnv) {
            Map<String, String> merged = new java.util.HashMap<>(System.getenv());
            if (env != null) {
                merged.putAll(env);
            }
            this.env = Map.copyOf(merged);
        } else {
            this.env = env != null ? Map.copyOf(env) : Map.of();
        }
    }

    /**
     * 与 {@link #LocalFilesystemWithShell(Path, boolean, int, int, Map, boolean, NamespaceFactory)}
     * 相同，但使用路径字符串；关于 {@code null} / 空白规则，请参见
     * {@link LocalFilesystem#LocalFilesystem(String)}。
     */
    public LocalFilesystemWithShell(
            String rootDir,
            boolean virtualMode,
            int timeout,
            int maxOutputBytes,
            Map<String, String> env,
            boolean inheritEnv,
            NamespaceFactory namespaceFactory) {
        this(
                LocalFilesystem.rootDirFromString(rootDir),
                virtualMode,
                timeout,
                maxOutputBytes,
                env,
                inheritEnv,
                namespaceFactory);
    }

    @Override
    public String id() {
        return sandboxId;
    }

    @Override
    public ExecuteResponse execute(
            RuntimeContext runtimeContext, String command, Integer timeoutSeconds) {
        if (command == null || command.isBlank()) {
            return new ExecuteResponse("Error: Command must be a non-empty string.", 1, false);
        }

        int effectiveTimeout = timeoutSeconds != null ? timeoutSeconds : defaultTimeout;
        if (effectiveTimeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + effectiveTimeout);
        }

        try {
            Path workDir = resolveExecuteCwd(runtimeContext);
            ProcessBuilder pb =
                    new ProcessBuilder("sh", "-c", command)
                            .directory(workDir.toFile())
                            .redirectErrorStream(false);

            if (!env.isEmpty()) {
                pb.environment().clear();
                pb.environment().putAll(env);
            }

            Process proc = pb.start();

            boolean finished = proc.waitFor(effectiveTimeout, TimeUnit.SECONDS);

            String stdout =
                    new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr =
                    new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!finished) {
                proc.destroyForcibly();
                String msg;
                if (timeoutSeconds != null) {
                    msg =
                            "Error: Command timed out after "
                                    + effectiveTimeout
                                    + " seconds (custom timeout). The command may be stuck or"
                                    + " require more time.";
                } else {
                    msg =
                            "Error: Command timed out after "
                                    + effectiveTimeout
                                    + " seconds. For long-running commands, re-run using the"
                                    + " timeout parameter.";
                }
                return new ExecuteResponse(msg, 124, false);
            }

            StringBuilder output = new StringBuilder();
            if (stdout != null && !stdout.isEmpty()) {
                output.append(stdout);
            }
            if (stderr != null && !stderr.isBlank()) {
                String[] stderrLines = stderr.strip().split("\n");
                for (String line : stderrLines) {
                    if (!output.isEmpty()) {
                        output.append('\n');
                    }
                    output.append("[stderr] ").append(line);
                }
            }

            String outputStr = output.isEmpty() ? "<no output>" : output.toString();

            boolean truncated = false;
            if (outputStr.length() > maxOutputBytes) {
                outputStr =
                        outputStr.substring(0, maxOutputBytes)
                                + "\n\n... Output truncated at "
                                + maxOutputBytes
                                + " bytes.";
                truncated = true;
            }

            int exitCode = proc.exitValue();
            if (exitCode != 0) {
                outputStr = outputStr.stripTrailing() + "\n\nExit code: " + exitCode;
            }

            return new ExecuteResponse(outputStr, exitCode, truncated);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Command execution failed: {}", e.getMessage(), e);
            return new ExecuteResponse(
                    "Error executing command ("
                            + e.getClass().getSimpleName()
                            + "): "
                            + e.getMessage(),
                    1,
                    false);
        }
    }

    private Path resolveExecuteCwd(RuntimeContext rc) {
        NamespaceFactory nsf = getNamespaceFactory();
        if (nsf == null) {
            return getCwd();
        }
        List<String> ns = nsf.getNamespace(rc);
        if (ns == null || ns.isEmpty()) {
            return getCwd();
        }
        Path namespaced = getCwd();
        for (String segment : ns) {
            namespaced = namespaced.resolve(segment);
        }
        try {
            Files.createDirectories(namespaced);
        } catch (IOException e) {
            log.warn("Failed to create namespace directory {}: {}", namespaced, e.getMessage());
        }
        return namespaced;
    }
}
