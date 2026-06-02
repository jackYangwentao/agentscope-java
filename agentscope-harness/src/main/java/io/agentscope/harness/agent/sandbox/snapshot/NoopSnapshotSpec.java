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
 * 创建 {@link NoopSandboxSnapshot} 实例的快照规范。
 * <p>
 * 使用此规范意味着工作区状态永远不会在会话停止之间持久化。
 * 每次会话启动都会从完整清单开始，以全新的工作区初始化。
 * <p>
 * Snapshot spec that creates {@link NoopSandboxSnapshot} instances.
 *
 * <p>Using this spec means workspace state is never persisted between session stops.
 * Every session start begins with a fresh workspace initialized from the full manifest.
 */
public class NoopSnapshotSpec implements SandboxSnapshotSpec {

    /** 创建空操作快照规范。Creates a noop snapshot spec. */
    public NoopSnapshotSpec() {}

    /**
     * {@inheritDoc}
     *
     * @return 新的 {@link NoopSandboxSnapshot}（忽略 {@code snapshotId}）
     * <p>
     * a new {@link NoopSandboxSnapshot} (ignores {@code snapshotId})
     */
    @Override
    public SandboxSnapshot build(String snapshotId) {
        return new NoopSandboxSnapshot();
    }
}
