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
package io.agentscope.harness.agent.sandbox.impl.e2b;

/**
 * E2B 沙箱工作空间字节的持久化方式。
 * How workspace bytes are persisted for E2B sandboxes.
 */
public enum E2bPersistenceMode {
    /** Tar 归档字节（默认值，与其他 Harness 快照兼容）。Tar archive bytes (default, compatible with other Harness snapshots). */
    TAR,
    /**
     * E2B {@code POST /sandboxes/{id}/snapshots} 加上 Harness 快照流中的 {@link E2bSnapshotRefs} 标记字节
     * （恢复时会以快照 ID 作为 {@code templateID} 重新创建沙箱）。
     * E2B {@code POST /sandboxes/{id}/snapshots} plus {@link E2bSnapshotRefs} marker bytes in the
     * Harness snapshot stream (restore recreates a sandbox with the snapshot id as {@code templateID}).
     */
    NATIVE_SNAPSHOT
}
