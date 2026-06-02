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

import java.nio.file.Path;

/**
 * 创建存储在本地目录中的 {@link LocalSandboxSnapshot} 实例的快照规范。
 * <p>
 * 每个会话在 {@code {basePath}/{sessionId}.tar} 拥有自己的快照文件。
 * <p>
 * Snapshot spec that creates {@link LocalSandboxSnapshot} instances stored in a local directory.
 *
 * <p>Each session gets its own snapshot file at {@code {basePath}/{sessionId}.tar}.
 */
public class LocalSnapshotSpec implements SandboxSnapshotSpec {

    private final String basePath;

    /**
     * 创建本地快照规范。
     * <p>
     * Creates a local snapshot spec.
     *
     * @param basePath 快照 tar 文件的存储目录
     */
    public LocalSnapshotSpec(Path basePath) {
        this.basePath = basePath.toString();
    }

    /**
     * 创建本地快照规范。
     * <p>
     * Creates a local snapshot spec.
     *
     * @param basePath 快照 tar 文件的存储目录路径字符串
     */
    public LocalSnapshotSpec(String basePath) {
        this.basePath = basePath;
    }

    /**
     * {@inheritDoc}
     *
     * @return 存储在 {@code {basePath}/{snapshotId}.tar} 的新 {@link LocalSandboxSnapshot}
     * <p>
     * a new {@link LocalSandboxSnapshot} storing at {@code {basePath}/{snapshotId}.tar}
     */
    @Override
    public SandboxSnapshot build(String snapshotId) {
        return new LocalSandboxSnapshot(basePath, snapshotId);
    }

    /**
     * 返回快照文件的基目录。
     * <p>
     * Returns the base directory used for snapshot files.
     *
     * @return 基路径字符串
     */
    public String getBasePath() {
        return basePath;
    }
}
