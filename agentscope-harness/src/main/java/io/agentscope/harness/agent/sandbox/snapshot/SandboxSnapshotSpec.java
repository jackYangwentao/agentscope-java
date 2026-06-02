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
package io.agentscope.harness.agent.sandbox.snapshot;

/**
 * 为给定会话 ID 创建 {@link SandboxSnapshot} 实例的工厂。
 * <p>
 * 实现配置快照的存储位置：
 * {@link NoopSnapshotSpec}（禁用）、{@link LocalSnapshotSpec}（本地磁盘）、
 * {@link RemoteSnapshotSpec}（远程存储）。
 * <p>
 * 传递给 {@link #build} 的 {@code snapshotId} 参数允许每个会话拥有自己独立的
 * 快照文件/对象，同时共享相同的存储配置。
 * <p>
 * Factory that creates {@link SandboxSnapshot} instances for a given session ID.
 *
 * <p>Implementations configure WHERE snapshots are stored:
 * {@link NoopSnapshotSpec} (disabled), {@link LocalSnapshotSpec} (local disk),
 * {@link RemoteSnapshotSpec} (remote storage).
 *
 * <p>The {@code snapshotId} parameter passed to {@link #build} allows each session to have
 * its own isolated snapshot file/object, while sharing the same storage configuration.
 */
public interface SandboxSnapshotSpec {

    /**
     * 为给定的会话 ID 创建 {@link SandboxSnapshot}。
     * <p>
     * Creates a {@link SandboxSnapshot} for the given session ID.
     *
     * @param snapshotId 快照的唯一标识符（通常为会话 UUID）
     * @return 为给定 ID 配置的新快照实例
     */
    SandboxSnapshot build(String snapshotId);
}
