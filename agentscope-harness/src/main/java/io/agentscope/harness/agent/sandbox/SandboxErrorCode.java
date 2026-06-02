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

/**
 * 沙箱操作的错误码，用于 {@link SandboxException} 及其子类。
 * Error codes for sandbox operations, used in {@link SandboxException} and its subclasses.
 */
public enum SandboxErrorCode {

    /** 命令以非零退出码结束。 */
    EXEC_NONZERO,

    /** 命令执行超时。 */
    EXEC_TIMEOUT,

    /** 启动工作空间后端失败。 */
    WORKSPACE_START_ERROR,

    /** 停止/持久化工作空间后端失败。 */
    WORKSPACE_STOP_ERROR,

    /** 读取或解析工作空间归档（tar）失败。 */
    WORKSPACE_ARCHIVE_READ_ERROR,

    /** 创建工作空间归档（tar）失败。 */
    WORKSPACE_ARCHIVE_WRITE_ERROR,

    /** 持久化快照失败。 */
    SNAPSHOT_PERSIST_ERROR,

    /** 恢复快照失败。 */
    SNAPSHOT_RESTORE_ERROR,

    /** 快照不存在或无法恢复。 */
    SNAPSHOT_NOT_RESTORABLE,

    /** 清单条目包含无效或不安全的路径。 */
    INVALID_MANIFEST_PATH,

    /** 无效或缺失的沙箱配置。 */
    CONFIGURATION_ERROR
}
