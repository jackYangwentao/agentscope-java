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

import io.agentscope.harness.agent.sandbox.SandboxErrorCode;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 将工作区归档持久化为本地文件系统 tar 文件的快照实现。
 * <p>
 * 归档采用原子写入方式：数据首先写入同一目录下的临时文件（前缀为 {@code .}），
 * 然后通过 {@link StandardCopyOption#ATOMIC_MOVE} 移动到最终路径。
 * 这确保快照要么完整写入，要么不存在——绝不出现部分写入状态。
 * <p>
 * 安全性要求：{@code id} 必须为单个路径段，不能包含 {@code /} 或 {@code ..} 字符，
 * 以防止路径遍历攻击。
 * <p>
 * Snapshot that persists workspace archives as tar files on the local filesystem.
 *
 * <p>Archives are written atomically: the data is first written to a temporary file
 * (prefixed with {@code .}) in the same directory, then moved to the final path using
 * {@link StandardCopyOption#ATOMIC_MOVE}. This ensures the snapshot is either fully
 * written or not present — never partially written.
 *
 * <p>Security: {@code id} must be a single path segment with no {@code /} or {@code ..}
 * characters to prevent path traversal attacks.
 */
public class LocalSandboxSnapshot implements SandboxSnapshot {

    private final String basePath;
    private final String id;

    /**
     * 创建本地快照。
     * <p>
     * Creates a local snapshot.
     *
     * @param basePath 快照 tar 文件存储目录
     * @param id       快照的唯一标识符（必须是安全的单路径段）
     * @throws IllegalArgumentException 如果 {@code id} 包含不安全的字符
     */
    public LocalSandboxSnapshot(String basePath, String id) {
        validateId(id);
        this.basePath = basePath;
        this.id = id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>将归档原子写入到 {@code {basePath}/{id}.tar}。
     * <p>
     * Writes the archive atomically to {@code {basePath}/{id}.tar}.
     */
    @Override
    public void persist(InputStream workspaceArchive) throws Exception {
        Path targetPath = Path.of(basePath).resolve(id + ".tar");
        Path tmpPath = targetPath.resolveSibling("." + id + "." + UUID.randomUUID() + ".tmp");

        try {
            Files.createDirectories(targetPath.getParent());
            try (OutputStream out =
                    Files.newOutputStream(
                            tmpPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                workspaceArchive.transferTo(out);
            }
            Files.move(
                    tmpPath,
                    targetPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmpPath);
            } catch (Exception ignored) {
                // 尽力清理临时文件
                // Best-effort cleanup of the temp file
            }
            throw new SandboxException.SnapshotException(id, "Failed to persist snapshot", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>打开 {@code {basePath}/{id}.tar} 快照 tar 文件进行读取。
     * <p>
     * Opens the snapshot tar file at {@code {basePath}/{id}.tar} for reading.
     */
    @Override
    public InputStream restore() throws Exception {
        Path path = Path.of(basePath).resolve(id + ".tar");
        if (!Files.exists(path)) {
            throw new SandboxException.SnapshotException(id);
        }
        try {
            return Files.newInputStream(path);
        } catch (Exception e) {
            throw new SandboxException.SnapshotException(id, "Failed to read snapshot", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return 如果快照 tar 文件存在则返回 {@code true}
     * <p>
     * {@code true} if the snapshot tar file exists
     */
    @Override
    public boolean isRestorable() {
        return Files.exists(Path.of(basePath).resolve(id + ".tar"));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return "local";
    }

    /**
     * 返回快照 tar 文件的存储基目录。
     * <p>
     * Returns the base directory where snapshot tar files are stored.
     *
     * @return 基路径字符串
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * 验证快照 ID 是否安全（不包含路径分隔符、父目录引用或空字符）。
     * <p>
     * Validate that the snapshot ID is safe (no path separators, parent refs, or null chars).
     */
    private static void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Snapshot id must not be null or blank");
        }
        if (id.contains("/") || id.contains("\\") || id.contains("..") || id.contains("\0")) {
            throw new SandboxException(
                    SandboxErrorCode.INVALID_MANIFEST_PATH,
                    "Snapshot id contains unsafe characters: " + id);
        }
    }
}
