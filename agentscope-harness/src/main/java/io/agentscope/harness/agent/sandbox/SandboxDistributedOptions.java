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

import io.agentscope.core.session.Session;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.snapshot.OssSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.RedisSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;

/**
 * 分布式沙箱的高层配置，由 {@link io.agentscope.harness.agent.HarnessAgent.Builder#sandboxDistributed} 使用。
 * High-level distributed sandbox configuration used by
 * {@link io.agentscope.harness.agent.HarnessAgent.Builder#sandboxDistributed}.
 *
 * <p>Bundles the pieces required for distributed sandbox restore/sharing that are not already on
 * {@link SandboxFilesystemSpec}:
 *
 * <ul>
 *   <li>distributed {@link Session} (for state-store slots)
 *   <li>optional {@link SandboxSnapshotSpec} override (workspace archive persistence)
 *   <li>{@code requireDistributed} — fail-fast when distributed prerequisites are not met
 * </ul>
 *
 * <p>Configure {@link io.agentscope.harness.agent.IsolationScope} on {@code SandboxFilesystemSpec}
 * only; it is not duplicated here.
 */
public final class SandboxDistributedOptions {

    private final Session session;
    private final SandboxSnapshotSpec snapshotSpec;
    private final boolean requireDistributed;

    private SandboxDistributedOptions(Builder builder) {
        this.session = builder.session;
        this.snapshotSpec = builder.snapshotSpec;
        this.requireDistributed = builder.requireDistributed;
    }

    /**
     * 创建具有安全分布式默认值的构建器。
     * Creates a builder with safe distributed defaults.
     *
     * <p>默认值：
     *
     * <ul>
     *   <li>{@code requireDistributed = true}
     * </ul>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建使用 OSS 快照后端和分布式安全默认值的选项。
     * Creates options with OSS snapshot backend and distributed-safe defaults.
     */
    public static SandboxDistributedOptions oss(Session session, OssSnapshotSpec snapshotSpec) {
        return builder().session(session).snapshotSpec(snapshotSpec).build();
    }

    /**
     * 创建使用 Redis 快照后端和分布式安全默认值的选项。
     * Creates options with Redis snapshot backend and distributed-safe defaults.
     */
    public static SandboxDistributedOptions redis(Session session, RedisSnapshotSpec snapshotSpec) {
        return builder().session(session).snapshotSpec(snapshotSpec).build();
    }

    /**
     * 返回 {@link SessionSandboxStateStore} 使用的分布式会话后端。
     * Returns the distributed session backend used by {@link SessionSandboxStateStore}.
     */
    public Session getSession() {
        return session;
    }

    /**
     * 返回用于工作空间归档持久化的快照规范。
     * Returns the snapshot spec used for workspace archive persistence.
     */
    public SandboxSnapshotSpec getSnapshotSpec() {
        return snapshotSpec;
    }

    /**
     * 分布式前置条件不满足时构建器是否应快速失败。
     * Whether builder should fail-fast when distributed prerequisites are not met.
     */
    public boolean isRequireDistributed() {
        return requireDistributed;
    }

    public static final class Builder {

        private Session session;
        private SandboxSnapshotSpec snapshotSpec;
        private boolean requireDistributed = true;

        private Builder() {}

        /**
         * 设置分布式会话后端（用于状态槽持久化）。
         * Sets distributed session backend (for state slot persistence).
         */
        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        /**
         * 设置用于工作空间持久化的快照策略。
         * Sets snapshot strategy used for workspace persistence.
         */
        public Builder snapshotSpec(SandboxSnapshotSpec snapshotSpec) {
            this.snapshotSpec = snapshotSpec;
            return this;
        }

        /**
         * 启用/禁用分布式前置条件的快速失败检查。
         * Enables/disables fail-fast checks for distributed prerequisites.
         *
         * <p>当 {@code true}（默认值）时，如果有效会话仍然是本地的
         * （{@code WorkspaceSession}）或快照规范缺失/无操作，构建器将抛出异常。
         */
        public Builder requireDistributed(boolean requireDistributed) {
            this.requireDistributed = requireDistributed;
            return this;
        }

        public SandboxDistributedOptions build() {
            return new SandboxDistributedOptions(this);
        }
    }
}
