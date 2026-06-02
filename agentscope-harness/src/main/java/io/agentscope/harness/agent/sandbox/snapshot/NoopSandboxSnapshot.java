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

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 不持久化任何内容的空操作快照。
 * <p>
 * 使用 {@code NoopSandboxSnapshot} 时，工作区状态在会话停止之间不会被保留。
 * 每次新启动会话时，都会完整应用清单（启动逻辑的分支 D）。
 * 当工作区持久化不是必需时使用此快照。
 * <p>
 * No-op snapshot that does not persist anything.
 *
 * <p>When using {@code NoopSandboxSnapshot}, workspace state is NOT preserved between
 * session stops. Each time a session is started fresh, the full manifest is applied
 * (Branch D of the start logic). Use this when workspace durability is not required.
 */
public class NoopSandboxSnapshot implements SandboxSnapshot {

    private static final String ID = "noop";

    /** 创建空操作快照。Creates a noop snapshot. */
    public NoopSandboxSnapshot() {}

    /**
     * {@inheritDoc}
     *
     * <p>返回 {@code false} —— 使用此快照时完全跳过工作区归档步骤，
     * 因此在正常操作中此方法不会被调用。
     * <p>
     * Returns {@code false} — workspace archiving is skipped entirely when this
     * snapshot is in use, so this method is never called in normal operation.
     */
    @Override
    public boolean isPersistenceEnabled() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>此实现完全丢弃归档流。
     * <p>
     * This implementation discards the archive stream entirely.
     */
    @Override
    public void persist(InputStream workspaceArchive) throws Exception {
        // 有意丢弃 —— 空操作快照不持久化任何内容
        // Intentionally discard — no-op snapshot does not persist anything
        if (workspaceArchive != null) {
            workspaceArchive.transferTo(OutputStream.nullOutputStream());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>始终抛出 {@link io.agentscope.harness.agent.sandbox.SandboxException.SnapshotException}，
     * 因为空操作快照永远不可恢复。
     * <p>
     * Always throws {@link io.agentscope.harness.agent.sandbox.SandboxException.SnapshotException}
     * since noop snapshots are never restorable.
     */
    @Override
    public InputStream restore() throws Exception {
        throw new io.agentscope.harness.agent.sandbox.SandboxException.SnapshotException(ID);
    }

    /**
     * {@inheritDoc}
     *
     * @return 始终返回 {@code false}
     * <p>
     * always {@code false}
     */
    @Override
    public boolean isRestorable() {
        return false;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getType() {
        return "noop";
    }
}
