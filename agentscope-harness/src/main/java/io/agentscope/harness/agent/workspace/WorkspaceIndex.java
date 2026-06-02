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
package io.agentscope.harness.agent.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Best-effort SQLite index for the local workspace.
 *
 * <p>Tracks files that have been materialized locally under two path prefixes:
 * {@code agents/&#42;/sessions/&#42;&#42;} and {@code memory/&#42;&#42;}. The index is used to speed up
 * {@code ls / glob / exists / grep} in remote-backed workspace mode by avoiding full-store
 * key scans when enumerating paths under a prefix. File <em>content</em> is never stored in
 * the index — {@code grep} still fetches each candidate file from the remote store
 * authoritatively.
 *
 * <p><strong>Consistency model:</strong> the index is best-effort and may lag remote changes.
 * Remote writes remain authoritative. Index update failures are silently logged and never
 * propagate to callers.
 *
 * <p><strong>Thread-safety:</strong> SQLite serialises concurrent writers through its own
 * transaction machinery. No external locks are required.
 */
/**
 * 本地工作区的最佳努力 SQLite 索引。
 *
 * <p>跟踪在本地物化的文件，作用于两个路径前缀：{@code agents/&#42;/sessions/&#42;&#42;}
 * 和 {@code memory/&#42;&#42;}。该索引用于加速远程支持的工作区模式下的
 * {@code ls / glob / exists / grep} 操作，避免在枚举前缀下的路径时进行完整的存储键扫描。
 * 文件<em>内容</em>从不存储在索引中——{@code grep} 仍然权威地从远程存储获取每个候选文件。
 *
 * <p><strong>一致性模型：</strong>索引采用最佳努力模式，可能滞后于远程更改。
 * 远程写入保持权威性。索引更新失败会被静默记录，不会传播给调用方。
 *
 * <p><strong>线程安全：</strong>SQLite 通过自身的事务机制序列化并发写入者。无需外部锁。
 */
public class WorkspaceIndex implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceIndex.class);

    /** Schema version stored in index_state; bump when the schema changes. */
    /** 存储在 index_state 中的模式版本号；模式变更时需更新。 */
    private static final int SCHEMA_VERSION = 1;

    private static final String INDEX_DIR = ".index";
    private static final String INDEX_DB = "workspace.db";

    /**
     * Path prefixes (workspace-relative) that are eligible for indexing.
     * Only files under one of these prefixes will be tracked.
     */
    /**
     * 符合索引条件的路径前缀（工作区相对路径）。
     * 仅在这些前缀下的文件会被跟踪。
     */
    private static final List<String> INDEXED_PREFIXES =
            List.of(WorkspaceConstants.AGENTS_DIR + "/", WorkspaceConstants.MEMORY_DIR + "/");

    private final Connection conn;

    // -------------------------------------------------------------------------
    //  Factory
    // -------------------------------------------------------------------------

    /**
     * Opens (or creates) the workspace index for the given workspace root.
     *
     * <p>Returns {@code null} if the index cannot be initialized — callers should treat a
     * {@code null} index as "unavailable" and fall back to remote scan.
     *
     * @param workspaceRoot absolute path to the workspace root directory
     * @return a ready-to-use {@link WorkspaceIndex}, or {@code null} on failure
     */
    /**
     * 打开（或创建）指定工作区根目录的工作区索引。
     *
     * <p>如果索引无法初始化，则返回 {@code null}——调用方应将 {@code null} 索引视为
     * "不可用"并回退到远程扫描。
     *
     * @param workspaceRoot 工作区根目录的绝对路径
     * @return 就绪可用的 {@link WorkspaceIndex}，或失败时返回 {@code null}
     */
    public static WorkspaceIndex open(Path workspaceRoot) {
        try {
            Path indexDir = workspaceRoot.resolve(INDEX_DIR);
            Files.createDirectories(indexDir);
            Path dbFile = indexDir.resolve(INDEX_DB);
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            Connection c = DriverManager.getConnection(url);
            WorkspaceIndex idx = new WorkspaceIndex(c);
            idx.initSchema();
            return idx;
        } catch (Exception e) {
            log.warn("WorkspaceIndex unavailable (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    private WorkspaceIndex(Connection conn) {
        this.conn = conn;
    }

    // -------------------------------------------------------------------------
    //  Schema
    // -------------------------------------------------------------------------

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS files ("
                            + "path         TEXT PRIMARY KEY,"
                            + "size_bytes   INTEGER,"
                            + "modified_at  TEXT,"
                            + "content_type TEXT,"
                            + "encoding     TEXT,"
                            + "present_local INTEGER DEFAULT 1"
                            + ")");
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS index_state ("
                            + "key TEXT PRIMARY KEY,"
                            + "value TEXT"
                            + ")");
            // Record schema version (INSERT OR IGNORE so we only write it once)
            st.executeUpdate(
                    "INSERT OR IGNORE INTO index_state(key, value) VALUES ('schema_version', '"
                            + SCHEMA_VERSION
                            + "')");
        }
    }

    // -------------------------------------------------------------------------
    //  Write operations
    // -------------------------------------------------------------------------

    /**
     * Upserts a file entry in the index. Silently no-ops if the path is not under an indexed
     * prefix, or if any error occurs.
     *
     * @param path workspace-relative path (forward slashes)
     * @param sizeBytes file size in bytes; pass {@code -1} if unknown
     * @param modifiedAt ISO-8601 timestamp string; pass {@code null} to use current time
     */
    /**
     * 在索引中更新插入文件条目。如果路径不在已索引的前缀下或发生任何错误，则静默无操作。
     *
     * @param path       工作区相对路径（正斜杠）
     * @param sizeBytes  文件大小（字节）；未知时传递 {@code -1}
     * @param modifiedAt ISO-8601 时间戳字符串；传递 {@code null} 使用当前时间
     */
    public void upsert(String path, long sizeBytes, String modifiedAt) {
        if (!isIndexable(path)) {
            return;
        }
        try {
            String ts = modifiedAt != null ? modifiedAt : Instant.now().toString();
            try (PreparedStatement ps =
                    conn.prepareStatement(
                            "INSERT INTO files(path, size_bytes, modified_at, present_local)"
                                    + " VALUES(?,?,?,1)"
                                    + " ON CONFLICT(path) DO UPDATE SET"
                                    + "  size_bytes=excluded.size_bytes,"
                                    + "  modified_at=excluded.modified_at,"
                                    + "  present_local=1")) {
                ps.setString(1, path);
                ps.setLong(2, sizeBytes);
                ps.setString(3, ts);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.debug("Index upsert failed for '{}' (non-fatal): {}", path, e.getMessage());
        }
    }

    /**
     * Convenience overload that reads file size from the local file if it exists.
     *
     * @param path workspace-relative path
     * @param localFile absolute path on disk (used for size / mtime); may not exist
     */
    /**
     * 便捷重载方法，如果本地文件存在则从其读取文件大小。
     *
     * @param path      工作区相对路径
     * @param localFile 磁盘上的绝对路径（用于获取大小/修改时间）；可能不存在
     */
    public void upsertFromLocalFile(String path, Path localFile) {
        if (!isIndexable(path)) {
            return;
        }
        try {
            long size = -1;
            String mtime = null;
            if (Files.isRegularFile(localFile)) {
                size = Files.size(localFile);
                mtime = Files.getLastModifiedTime(localFile).toInstant().toString();
            }
            upsert(path, size, mtime);
        } catch (IOException e) {
            log.debug("Index upsert (stat) failed for '{}' (non-fatal): {}", path, e.getMessage());
        }
    }

    /**
     * Removes a file entry from the index. Silently no-ops on errors.
     *
     * @param path workspace-relative path
     */
    /**
     * 从索引中移除文件条目。错误时静默无操作。
     *
     * @param path 工作区相对路径
     */
    public void remove(String path) {
        if (!isIndexable(path)) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM files WHERE path=?")) {
            ps.setString(1, path);
            ps.executeUpdate();
        } catch (Exception e) {
            log.debug("Index remove failed for '{}' (non-fatal): {}", path, e.getMessage());
        }
    }

    /**
     * Renames (moves) an index entry from {@code fromPath} to {@code toPath}. Silently no-ops on
     * errors.
     */
    /**
     * 将索引条目从 {@code fromPath} 重命名（移动）到 {@code toPath}。错误时静默无操作。
     */
    public void rename(String fromPath, String toPath) {
        if (!isIndexable(fromPath) && !isIndexable(toPath)) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE files SET path=? WHERE path=?")) {
            ps.setString(1, toPath);
            ps.setString(2, fromPath);
            ps.executeUpdate();
        } catch (Exception e) {
            log.debug(
                    "Index rename failed '{}' -> '{}' (non-fatal): {}",
                    fromPath,
                    toPath,
                    e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    //  Read operations
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the index contains an entry for the given path with
     * {@code present_local = 1}.
     */
    /**
     * 如果索引包含指定路径且 {@code present_local = 1} 的条目，则返回 {@code true}。
     */
    public boolean exists(String path) {
        if (!isIndexable(path)) {
            return false;
        }
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "SELECT 1 FROM files WHERE path=? AND present_local=1 LIMIT 1")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.debug("Index exists failed for '{}' (non-fatal): {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Returns all locally-present paths that start with the given prefix.
     *
     * @param prefix workspace-relative directory prefix (e.g. {@code agents/a1/sessions/})
     * @return list of matching paths, may be empty
     */
    /**
     * 返回所有以指定前缀开头的本地存在的路径。
     *
     * @param prefix 工作区相对目录前缀（例如 {@code agents/a1/sessions/}）
     * @return 匹配的路径列表，可能为空
     */
    public List<String> listByPrefix(String prefix) {
        List<String> result = new ArrayList<>();
        if (prefix == null) {
            return result;
        }
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "SELECT path FROM files WHERE path LIKE ? AND present_local=1")) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            log.debug("Index listByPrefix failed for '{}' (non-fatal): {}", prefix, e.getMessage());
        }
        return result;
    }

    /**
     * Returns true if the index has any entries under the given prefix. Faster than
     * {@link #listByPrefix} when only presence is needed.
     */
    /**
     * 如果索引在指定前缀下有任何条目，则返回 true。当仅需判断是否存在时，
     * 比 {@link #listByPrefix} 更快。
     */
    public boolean hasPrefix(String prefix) {
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "SELECT 1 FROM files WHERE path LIKE ? AND present_local=1 LIMIT 1")) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.debug("Index hasPrefix failed (non-fatal): {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    //  Rebuild
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the index by walking the local workspace directories
     * ({@code agents/&#42;/sessions} and {@code memory}). Existing entries are replaced;
     * stale entries for files that no longer exist are removed.
     *
     * <p>This is a best-effort operation: errors are logged and do not throw.
     *
     * @param workspaceRoot absolute path to workspace root
     */
    /**
     * 通过遍历本地工作区目录（{@code agents/&#42;/sessions} 和 {@code memory}）来重建索引。
     * 现有条目被替换；不存在的文件的过期条目被移除。
     *
     * <p>这是最佳努力操作：错误会被记录但不会抛出异常。
     *
     * @param workspaceRoot 工作区根目录的绝对路径
     */
    public void rebuildFromDisk(Path workspaceRoot) {
        try {
            // Clear existing entries / 清除现有条目
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM files");
            }

            for (String prefix : INDEXED_PREFIXES) {
                Path dir = workspaceRoot.resolve(prefix);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (var stream = Files.walk(dir)) {
                    stream.filter(Files::isRegularFile)
                            .forEach(
                                    file -> {
                                        Path rel =
                                                workspaceRoot
                                                        .toAbsolutePath()
                                                        .relativize(file.toAbsolutePath());
                                        String relStr = rel.toString().replace('\\', '/');
                                        upsertFromLocalFile(relStr, file);
                                    });
                }
            }
            log.info("WorkspaceIndex rebuilt from local disk");
        } catch (Exception e) {
            log.warn("WorkspaceIndex rebuild failed (non-fatal): {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    //  Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            log.debug("WorkspaceIndex close error (non-fatal): {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given workspace-relative path falls under one of the
     * indexed directory prefixes.
     */
    /**
     * 如果指定工作区相对路径属于某个已索引的目录前缀，则返回 {@code true}。
     */
    static boolean isIndexable(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String prefix : INDEXED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
