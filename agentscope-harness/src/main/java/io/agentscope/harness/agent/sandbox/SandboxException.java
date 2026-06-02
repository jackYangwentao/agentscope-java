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

import java.nio.file.Path;

/**
 * 所有沙箱操作的基础异常。
 * Base exception for all sandbox operations.
 *
 * <p>为常见故障模式提供了专门的静态内部子类：
 * {@link SandboxConfigurationException}, {@link SandboxRuntimeException},
 * {@link WorkspaceStartException}, {@link WorkspaceStopException},
 * {@link ExecException}, {@link ExecTimeoutException}, {@link SnapshotException}。
 */
public class SandboxException extends RuntimeException {

    private final SandboxErrorCode errorCode;
    private final String op;

    /**
     * 创建带有错误码和消息的沙箱异常。
     * Creates a sandbox exception with an error code and message.
     *
     * @param errorCode 分类故障的错误码
     * @param message 人类可读的描述
     */
    public SandboxException(SandboxErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.op = null;
    }

    /**
     * 创建带有错误码、消息和原因的沙箱异常。
     * Creates a sandbox exception with an error code, message, and cause.
     *
     * @param errorCode 分类故障的错误码
     * @param message 人类可读的描述
     * @param cause 根本原因
     */
    public SandboxException(SandboxErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.op = null;
    }

    /**
     * 创建带有错误码、操作名称、消息和原因的沙箱异常。
     * Creates a sandbox exception with an error code, operation name, message, and cause.
     *
     * @param errorCode 分类故障的错误码
     * @param op 失败的操作（例如 "start", "exec", "persist"）
     * @param message 人类可读的描述
     * @param cause 根本原因
     */
    public SandboxException(
            SandboxErrorCode errorCode, String op, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.op = op;
    }

    /**
     * 返回分类此故障的错误码。
     * Returns the error code classifying this failure.
     *
     * @return 错误码
     */
    public SandboxErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 返回失败的操作名称，如果未指定则返回 {@code null}。
     * Returns the operation name that failed, or {@code null} if not specified.
     *
     * @return 操作名称，可能为 null
     */
    public String getOp() {
        return op;
    }

    // ---- Specialized subclasses ----

    /**
     * 当沙箱配置无效或缺失时抛出（例如未配置客户端）。
     * Thrown when sandbox configuration is invalid or missing (e.g. no client configured).
     */
    public static class SandboxConfigurationException extends SandboxException {

        /**
         * Creates a configuration exception.
         *
         * @param message description of the configuration problem
         */
        public SandboxConfigurationException(String message) {
            super(SandboxErrorCode.CONFIGURATION_ERROR, message);
        }

        /**
         * Creates a configuration exception with cause.
         *
         * @param message description of the configuration problem
         * @param cause the underlying cause
         */
        public SandboxConfigurationException(String message, Throwable cause) {
            super(SandboxErrorCode.CONFIGURATION_ERROR, message, cause);
        }
    }

    /**
     * 为未被更具体子类覆盖的通用沙箱运行时故障抛出。
     * Thrown for general sandbox runtime failures not covered by a more specific subclass.
     */
    public static class SandboxRuntimeException extends SandboxException {

        /**
         * Creates a runtime exception with an error code and message.
         *
         * @param errorCode the error code classifying the failure
         * @param message human-readable description
         */
        public SandboxRuntimeException(SandboxErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        /**
         * Creates a runtime exception with an error code, message, and cause.
         *
         * @param errorCode the error code classifying the failure
         * @param message human-readable description
         * @param cause the underlying cause
         */
        public SandboxRuntimeException(
                SandboxErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }

        /**
         * Creates a runtime exception with a message and cause.
         *
         * @param message human-readable description
         * @param cause the underlying cause
         */
        public SandboxRuntimeException(String message, Throwable cause) {
            super(SandboxErrorCode.WORKSPACE_START_ERROR, message, cause);
        }
    }

    /**
     * 当工作空间后端启动失败时抛出。
     * Thrown when the workspace backend fails to start.
     */
    public static class WorkspaceStartException extends SandboxException {

        private final Path path;

        /**
         * Creates a workspace-start exception.
         *
         * @param path the workspace path that could not be started
         * @param cause the underlying cause
         */
        public WorkspaceStartException(Path path, Throwable cause) {
            super(
                    SandboxErrorCode.WORKSPACE_START_ERROR,
                    "Failed to start workspace at: " + path,
                    cause);
            this.path = path;
        }

        /**
         * Returns the workspace path that failed to start.
         *
         * @return workspace path
         */
        public Path getPath() {
            return path;
        }
    }

    /**
     * 当工作空间后端停止失败时抛出。
     * Thrown when the workspace backend fails to stop.
     */
    public static class WorkspaceStopException extends SandboxException {

        private final Path path;

        /**
         * Creates a workspace-stop exception.
         *
         * @param path the workspace path that could not be stopped
         * @param cause the underlying cause
         */
        public WorkspaceStopException(Path path, Throwable cause) {
            super(
                    SandboxErrorCode.WORKSPACE_STOP_ERROR,
                    "Failed to stop workspace at: " + path,
                    cause);
            this.path = path;
        }

        /**
         * Returns the workspace path that failed to stop.
         *
         * @return workspace path
         */
        public Path getPath() {
            return path;
        }
    }

    /**
     * 当沙箱命令以非零退出码结束时抛出。
     * Thrown when a sandbox command exits with a non-zero exit code.
     */
    public static class ExecException extends SandboxException {

        private final int exitCode;
        private final String stdout;
        private final String stderr;

        /**
         * Creates an exec exception.
         *
         * @param exitCode the process exit code
         * @param stdout captured standard output
         * @param stderr captured standard error
         */
        public ExecException(int exitCode, String stdout, String stderr) {
            super(
                    SandboxErrorCode.EXEC_NONZERO,
                    "Command exited with code " + exitCode + ": " + stderr);
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        /**
         * Returns the process exit code.
         *
         * @return exit code
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * Returns captured standard output.
         *
         * @return stdout
         */
        public String getStdout() {
            return stdout;
        }

        /**
         * Returns captured standard error.
         *
         * @return stderr
         */
        public String getStderr() {
            return stderr;
        }
    }

    /**
     * 当沙箱命令超时时抛出。
     * Thrown when a sandbox command times out.
     */
    public static class ExecTimeoutException extends SandboxException {

        /**
         * Creates an exec timeout exception.
         *
         * @param command the command that timed out
         * @param timeoutSeconds the timeout that was exceeded
         */
        public ExecTimeoutException(String command, int timeoutSeconds) {
            super(
                    SandboxErrorCode.EXEC_TIMEOUT,
                    "Command timed out after " + timeoutSeconds + "s: " + command);
        }
    }

    /**
     * 当快照操作（持久化或恢复）失败时抛出。
     * Thrown when a snapshot operation (persist or restore) fails.
     */
    public static class SnapshotException extends SandboxException {

        private final String snapshotId;

        /**
         * Creates a snapshot exception.
         *
         * @param snapshotId the snapshot identifier
         * @param message human-readable description
         * @param cause the underlying cause
         */
        public SnapshotException(String snapshotId, String message, Throwable cause) {
            super(SandboxErrorCode.SNAPSHOT_PERSIST_ERROR, message, cause);
            this.snapshotId = snapshotId;
        }

        /**
         * Creates a snapshot exception for a non-restorable snapshot.
         *
         * @param snapshotId the snapshot identifier
         */
        public SnapshotException(String snapshotId) {
            super(
                    SandboxErrorCode.SNAPSHOT_NOT_RESTORABLE,
                    "Snapshot is not restorable: " + snapshotId);
            this.snapshotId = snapshotId;
        }

        /**
         * Returns the snapshot identifier associated with this failure.
         *
         * @return snapshot id
         */
        public String getSnapshotId() {
            return snapshotId;
        }
    }
}
