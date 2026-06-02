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

import static io.agentscope.harness.agent.workspace.WorkspaceConstants.AGENTS_DIR;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.AGENTS_MD;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.KNOWLEDGE_DIR;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.KNOWLEDGE_MD;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.MEMORY_DIR;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.MEMORY_MD;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.SESSIONS_DIR;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.SESSIONS_STORE;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.SKILLS_DIR;
import static io.agentscope.harness.agent.workspace.WorkspaceConstants.TASKS_DIR;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.FileInfo;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.store.NamespaceFactory;
import io.agentscope.harness.agent.subagent.task.TaskRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateless accessor for workspace content using a two-layer read architecture.
 *
 * <p><strong>Read path:</strong> For every read (AGENTS.md, MEMORY.md, knowledge, etc.),
 * the {@link AbstractFilesystem} is queried first. If it returns non-empty content, that
 * content is used (filesystem overrides). Otherwise, the local workspace disk is read as a
 * fallback. The filesystem layer applies user/session scoping transparently via
 * {@link NamespaceFactory}.
 *
 * <p><strong>Write path:</strong> All writes (memory, sessions, etc.) go through the
 * {@link AbstractFilesystem}.
 *
 * <p><strong>Listing:</strong> File listings (memory files, knowledge files, session logs) union
 * results from both the filesystem layer and local disk, deduplicating by relative path.
 *
 * <p>Expected layout:
 *
 * <pre>
 * workspace/
 * ├── AGENTS.md
 * ├── MEMORY.md
 * ├── memory/YYYY-MM-DD.md
 * ├── skills/&lt;skill-name&gt;/SKILL.md
 * ├── knowledge/KNOWLEDGE.md
 * ├── knowledge/*
 * ├── subagents/&lt;id&gt;.md                     (subagent declarations)
 * ├── agents/&lt;agentId&gt;/workspace/           (isolated subagent runtime root, auto-created)
 * ├── agents/&lt;agentId&gt;/sessions/sessions.json
 * └── agents/&lt;agentId&gt;/sessions/&lt;sessionId&gt;.log.jsonl
 * </pre>
 */
/**
 * 使用双层读取架构的工作区内容无状态访问器。
 *
 * <p><strong>读取路径：</strong>对于每次读取（AGENTS.md、MEMORY.md、knowledge 等），
 * 首先查询 {@link AbstractFilesystem}。如果返回非空内容，则使用该内容（文件系统覆盖）。
 * 否则，回退到读取本地工作区磁盘。文件系统层通过 {@link NamespaceFactory} 透明地
 * 应用用户/会话范围。
 *
 * <p><strong>写入路径：</strong>所有写入操作（memory、sessions 等）都通过
 * {@link AbstractFilesystem} 进行。
 *
 * <p><strong>列出：</strong>文件列表（记忆文件、知识文件、会话日志）合并来自文件系统层
 * 和本地磁盘的结果，按相对路径去重。
 *
 * <p>预期布局：
 *
 * <pre>
 * workspace/
 * ├── AGENTS.md
 * ├── MEMORY.md
 * ├── memory/YYYY-MM-DD.md
 * ├── skills/&lt;skill-name&gt;/SKILL.md
 * ├── knowledge/KNOWLEDGE.md
 * ├── knowledge/*
 * ├── subagents/&lt;id&gt;.md                     (子代理声明)
 * ├── agents/&lt;agentId&gt;/workspace/           (隔离的子代理运行时根目录，自动创建)
 * ├── agents/&lt;agentId&gt;/sessions/sessions.json
 * └── agents/&lt;agentId&gt;/sessions/&lt;sessionId&gt;.log.jsonl
 * </pre>
 */
public class WorkspaceManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);
    private static final ObjectMapper SESSION_STORE_JSON = new ObjectMapper();
    private static final ObjectMapper TASK_RECORD_JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final TypeReference<Map<String, TaskRecord>> TASK_MAP_TYPE =
            new TypeReference<>() {};

    /**
     * Per-path locks for workspace-relative files to prevent concurrent read-modify-write races.
     * Keyed by workspace-relative path (e.g. {@code agents/X/tasks/Y.json},
     * {@code agents/X/sessions/sessions.json}, {@code memory/YYYY-MM-DD.md}).
     *
     * <p>This is an in-process lock only. For cross-process (multi-node) deployments the Remote
     * backend must additionally use server-side CAS / optimistic locking.
     */
    /**
     * 针对工作区相对文件的每路径锁，防止并发读写-修改-写入竞争。
     * 键为工作区相对路径（例如 {@code agents/X/tasks/Y.json}、
     * {@code agents/X/sessions/sessions.json}、{@code memory/YYYY-MM-DD.md}）。
     *
     * <p>此锁仅在单进程内有效。对于跨进程（多节点）部署，远程后端必须额外使用
     * 服务器端的 CAS / 乐观锁。
     */
    private final Map<String, ReentrantLock> pathLocks = new ConcurrentHashMap<>();

    private final Path workspace;
    private final AbstractFilesystem filesystem;

    /** Best-effort local file index; may be {@code null} if SQLite is unavailable. */
    /** 最佳努力的本地文件索引；如果 SQLite 不可用，则可能为 {@code null}。 */
    private final WorkspaceIndex index;

    private final NamespaceFactory namespaceFactory;

    /**
     * {@code true} when this manager allocated its own {@link #index} (via the {@code (workspace,
     * filesystem)} constructor) and is therefore responsible for closing it. When the index is
     * supplied externally (e.g. by {@link io.agentscope.harness.agent.HarnessAgent}'s builder), the
     * external owner manages its lifecycle and {@link #close()} here is a no-op.
     */
    /**
     * 当此管理器自行分配了 {@link #index}（通过 {@code (workspace, filesystem)} 构造函数）
     * 时，此值为 {@code true}，因此负责关闭它。当索引由外部提供时
     * （例如由 {@link io.agentscope.harness.agent.HarnessAgent} 的构建器提供），
     * 外部所有者管理其生命周期，此处的 {@link #close()} 为空操作。
     */
    private final boolean ownsIndex;

    public WorkspaceManager(Path workspace) {
        this(workspace, null, null, null, false);
    }

    public WorkspaceManager(Path workspace, AbstractFilesystem filesystem) {
        this(workspace, filesystem, WorkspaceIndex.open(workspace), null, true);
    }

    public WorkspaceManager(Path workspace, AbstractFilesystem filesystem, WorkspaceIndex index) {
        this(workspace, filesystem, index, null, false);
    }

    public WorkspaceManager(
            Path workspace,
            AbstractFilesystem filesystem,
            WorkspaceIndex index,
            NamespaceFactory namespaceFactory) {
        this(workspace, filesystem, index, namespaceFactory, false);
    }

    private WorkspaceManager(
            Path workspace,
            AbstractFilesystem filesystem,
            WorkspaceIndex index,
            NamespaceFactory namespaceFactory,
            boolean ownsIndex) {
        this.workspace = workspace;
        this.filesystem = filesystem;
        this.index = index;
        this.namespaceFactory = namespaceFactory;
        this.ownsIndex = ownsIndex;
    }

    /**
     * Releases the SQLite-backed {@link WorkspaceIndex} when this manager owns it.
     *
     * <p>Required for tests that use {@code @TempDir} on Windows: the JDBC driver keeps a file
     * handle on {@code .index/workspace.db}, and Windows refuses to delete the temp directory
     * while the handle is open.
     */
    /**
     * 当此管理器拥有 SQLite 支持的 {@link WorkspaceIndex} 时释放它。
     *
     * <p>对于在 Windows 上使用 {@code @TempDir} 的测试是必需的：JDBC 驱动在
     * {@code .index/workspace.db} 上保持文件句柄，而 Windows 在句柄打开时拒绝删除临时目录。
     */
    @Override
    public void close() {
        if (ownsIndex && index != null) {
            index.close();
        }
    }

    public NamespaceFactory getNamespaceFactory() {
        return namespaceFactory;
    }

    /** Returns the best-effort workspace index; may be {@code null} when unavailable. */
    /** 返回最佳努力的工作区索引；不可用时可能为 {@code null}。 */
    public WorkspaceIndex getIndex() {
        return index;
    }

    public AbstractFilesystem getFilesystem() {
        return filesystem;
    }

    /**
     * Validates the workspace exists and key files are present. Logs warnings for anything
     * missing. Called once at HarnessAgent build time.
     */
    /**
     * 验证工作区存在且关键文件已存在。对任何缺失的内容记录警告。
     * 在 HarnessAgent 构建时调用一次。
     */
    public void validate() {
        if (!Files.isDirectory(workspace)) {
            log.warn(
                    "Workspace directory does not exist: {}. "
                            + "Please create it and add AGENTS.md.",
                    workspace.toAbsolutePath());
            return;
        }
        boolean agentsMdExists = Files.isRegularFile(workspace.resolve(AGENTS_MD));
        if (!agentsMdExists && filesystem != null) {
            try {
                agentsMdExists = filesystem.exists(RuntimeContext.empty(), AGENTS_MD);
            } catch (Exception e) {
                log.debug(
                        "Filesystem not available at build time, skipping exists check: {}",
                        e.getMessage());
            }
        }
        if (!agentsMdExists) {
            log.warn(
                    "AGENTS.md not found in workspace: {}. "
                            + "AGENTS.md defines persona and local conventions for the agent.",
                    workspace.toAbsolutePath());
        }
    }

    public Path getWorkspace() {
        return workspace;
    }

    /**
     * Resolves a workspace-relative path for runtime user data, applying namespace prefix.
     * Use for paths that contain per-user data (sessions, tasks, memory).
     *
     * <p>The runtime context is forwarded to the {@link NamespaceFactory} so per-call
     * identity (user/session) drives the namespace, not a shared mutable reference.
     */
    /**
     * 为运行时用户数据解析工作区相对路径，应用命名空间前缀。
     * 用于包含按用户数据的路径（会话、任务、记忆）。
     *
     * <p>运行时上下文被转发给 {@link NamespaceFactory}，因此每次调用的身份
     * （用户/会话）驱动命名空间，而非共享的可变引用。
     */
    public Path resolveRuntimeDataPath(RuntimeContext rc, String relativePath) {
        if (namespaceFactory == null) {
            return workspace.resolve(relativePath);
        }
        List<String> ns = namespaceFactory.getNamespace(rc != null ? rc : RuntimeContext.empty());
        if (ns == null || ns.isEmpty()) {
            return workspace.resolve(relativePath);
        }
        return workspace.resolve(String.join("/", ns)).resolve(relativePath);
    }

    /** Reads AGENTS.md content, returns empty string if not found. */
    /** 读取 AGENTS.md 内容，如果未找到则返回空字符串。 */
    public String readAgentsMd(RuntimeContext rc) {
        return readWithOverride(rc, AGENTS_MD);
    }

    /** Reads KNOWLEDGE.md content from the knowledge directory. */
    /** 从知识目录读取 KNOWLEDGE.md 内容。 */
    public String readKnowledgeMd(RuntimeContext rc) {
        return readWithOverride(rc, KNOWLEDGE_DIR + "/" + KNOWLEDGE_MD);
    }

    /** Reads MEMORY.md content (two-layer: filesystem override, local fallback). */
    /** 读取 MEMORY.md 内容（双层：先文件系统覆盖，再本地回退）。 */
    public String readMemoryMd(RuntimeContext rc) {
        return readWithOverride(rc, MEMORY_MD);
    }

    /**
     * Reads a UTF-8 file under the workspace, using the two-layer pattern:
     * filesystem first, then local disk fallback.
     */
    /**
     * 读取工作区下的 UTF-8 文件，使用双层模式：先文件系统，再本地磁盘回退。
     */
    public String readManagedWorkspaceFileUtf8(RuntimeContext rc, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String normalized = normalizeRelativePath(relativePath);
        if (normalized.isEmpty()) {
            return "";
        }
        Path resolved = workspace.resolve(normalized).normalize();
        if (!resolved.startsWith(workspace)) {
            return "";
        }
        return readWithOverride(rc, normalized);
    }

    public Path getMemoryDir(RuntimeContext rc) {
        return resolveRuntimeDataPath(rc, MEMORY_DIR);
    }

    public Path getSkillsDir() {
        return workspace.resolve(SKILLS_DIR);
    }

    public Path getKnowledgeDir() {
        return workspace.resolve(KNOWLEDGE_DIR);
    }

    /** Lists all files under the knowledge directory tree (union of filesystem + local disk). */
    /** 列出知识目录树下的所有文件（文件系统和本地磁盘的并集）。 */
    public List<Path> listKnowledgeFiles(RuntimeContext rc) {
        Set<String> relativePaths = new LinkedHashSet<>();

        if (filesystem != null) {
            GlobResult glob = filesystem.glob(rc, "*", KNOWLEDGE_DIR);
            if (glob.isSuccess() && glob.matches() != null) {
                for (FileInfo fi : glob.matches()) {
                    if (fi.path() != null && !fi.path().isBlank()) {
                        relativePaths.add(normalizeRelativePath(fi.path().trim()));
                    }
                }
            }
        }

        Path dir = getKnowledgeDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .forEach(
                                p -> {
                                    String rel =
                                            workspace
                                                    .relativize(p.normalize())
                                                    .toString()
                                                    .replace('\\', '/');
                                    relativePaths.add(rel);
                                });
            } catch (IOException e) {
                log.warn("Failed to list knowledge files: {}", e.getMessage());
            }
        }

        List<Path> result = new ArrayList<>();
        for (String rel : relativePaths) {
            result.add(workspace.resolve(rel));
        }
        return result;
    }

    public Path getSessionDir(RuntimeContext rc, String agentId) {
        return resolveRuntimeDataPath(rc, AGENTS_DIR + "/" + agentId + "/" + SESSIONS_DIR);
    }

    /**
     * Returns the legacy session file path (.json) without creating directories.
     *
     * @deprecated Use {@link #resolveSessionContextFile(RuntimeContext, String, String)} for the
     *     JSONL format.
     */
    /**
     * 返回旧版会话文件路径 (.json)，不创建目录。
     *
     * @deprecated 请使用 {@link #resolveSessionContextFile(RuntimeContext, String, String)}
     *     获取 JSONL 格式。
     */
    @Deprecated
    public Path resolveSessionFile(RuntimeContext rc, String agentId, String sessionId) {
        return getSessionDir(rc, agentId).resolve(sessionId + ".json");
    }

    /** Returns the JSONL session context file path (LLM-facing, compacted). */
    /** 返回 JSONL 会话上下文文件路径（面向 LLM，已压缩）。 */
    public Path resolveSessionContextFile(RuntimeContext rc, String agentId, String sessionId) {
        return getSessionDir(rc, agentId)
                .resolve(sessionId + WorkspaceConstants.SESSION_CONTEXT_EXT);
    }

    /** Returns the JSONL session log file path (full history, append-only). */
    /** 返回 JSONL 会话日志文件路径（完整历史，仅追加）。 */
    public Path resolveSessionLogFile(RuntimeContext rc, String agentId, String sessionId) {
        return getSessionDir(rc, agentId).resolve(sessionId + WorkspaceConstants.SESSION_LOG_EXT);
    }

    /**
     * Appends UTF-8 text to a workspace-relative file, creating parent directories when needed.
     * All writes go through the {@link AbstractFilesystem}.
     *
     * <p>A per-path {@link ReentrantLock} serialises concurrent callers so that the
     * read→merge→write cycle is atomic within this process. For cross-process / multi-node
     * deployments the {@link AbstractFilesystem} backend must additionally provide server-side
     * concurrency control.
     */
    /**
     * 将 UTF-8 文本追加到工作区相对文件，在需要时创建父目录。
     * 所有写入操作都通过 {@link AbstractFilesystem}。
     *
     * <p>每路径的 {@link ReentrantLock} 序列化并发调用者，使得读取→合并→写入
     * 循环在此进程内是原子的。对于跨进程/多节点部署，{@link AbstractFilesystem}
     * 后端必须额外提供服务器端并发控制。
     */
    public void appendUtf8WorkspaceRelative(
            RuntimeContext rc, String relativePath, String content) {
        if (relativePath == null || content == null) {
            return;
        }
        String normalized = normalizeRelativePath(relativePath);
        if (normalized.isEmpty()) {
            return;
        }
        ReentrantLock lock = pathLocks.computeIfAbsent(normalized, k -> new ReentrantLock());
        lock.lock();
        try {
            if (filesystem == null) {
                appendLocalFile(normalized, content);
                return;
            }
            ReadResult rr = filesystem.read(rc, normalized, 0, 0);
            String existing = "";
            if (rr.isSuccess() && rr.fileData() != null && rr.fileData().content() != null) {
                existing = rr.fileData().content();
            }
            String merged = existing + content;
            filesystem.uploadFiles(
                    rc, List.of(Map.entry(normalized, merged.getBytes(StandardCharsets.UTF_8))));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Upserts metadata for a session in {@code agents/&lt;agentId&gt;/sessions/sessions.json}
     * (small mutable JSON, keyed by {@code sessionId}).
     *
     * <p>A per-path {@link ReentrantLock} serialises concurrent callers so that the
     * read→merge→write cycle is atomic within this process.
     */
    /**
     * 在 {@code agents/&lt;agentId&gt;/sessions/sessions.json} 中更新插入会话元数据
     * （小型可变 JSON，以 {@code sessionId} 为键）。
     *
     * <p>每路径的 {@link ReentrantLock} 序列化并发调用者，使得读取→合并→写入
     * 循环在此进程内是原子的。
     */
    public void updateSessionIndex(
            RuntimeContext rc, String agentId, String sessionId, String summary) {
        if (agentId == null || agentId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String rel = AGENTS_DIR + "/" + agentId + "/" + SESSIONS_DIR + "/" + SESSIONS_STORE;
        ReentrantLock lock = pathLocks.computeIfAbsent(rel, k -> new ReentrantLock());
        lock.lock();
        try {
            String existing = readWritableWorkspaceRelativeUtf8(rc, rel);
            ObjectNode root = parseSessionStoreOrEmpty(existing);
            ObjectNode sessions = ensureSessionsObject(root);
            ObjectNode entry = SESSION_STORE_JSON.createObjectNode();
            entry.put("summary", summary != null ? summary : "");
            entry.put("updatedAt", java.time.Instant.now().toString());
            sessions.set(sessionId, entry);
            if (!root.has("version")) {
                root.put("version", 1);
            }
            try {
                String serialized =
                        SESSION_STORE_JSON
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(root);
                writeUtf8WorkspaceRelative(rc, rel, serialized);
            } catch (IOException e) {
                log.warn("Failed to write session store {}: {}", rel, e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    // ==================== Task record methods ====================
    // ==================== 任务记录方法 ====================

    /**
     * Upserts a {@link TaskRecord} in {@code agents/<agentId>/tasks/<sessionId>.json}.
     *
     * <p>Reads the existing map, merges or inserts the record keyed by {@code taskId}, then
     * writes the updated map back. Acquires a per-file {@link ReentrantLock} to prevent
     * concurrent read-modify-write races when multiple tasks share the same session file.
     */
    /**
     * 在 {@code agents/<agentId>/tasks/<sessionId>.json} 中更新插入 {@link TaskRecord}。
     *
     * <p>读取现有映射，合并或插入以 {@code taskId} 为键的记录，然后将更新后的映射写回。
     * 获取每文件的 {@link ReentrantLock} 以防止多个任务共享同一会话文件时的
     * 并发读写-修改-写入竞争。
     */
    public void writeTaskRecord(
            RuntimeContext rc, String agentId, String sessionId, TaskRecord record) {
        if (agentId == null
                || agentId.isBlank()
                || sessionId == null
                || sessionId.isBlank()
                || record == null
                || record.getTaskId() == null) {
            return;
        }
        String rel = taskRecordPath(agentId, sessionId);
        ReentrantLock lock = pathLocks.computeIfAbsent(rel, k -> new ReentrantLock());
        lock.lock();
        try {
            Map<String, TaskRecord> map;
            try {
                map = readTaskMap(rc, rel); // already holding lock / 已持有锁
            } catch (IOException e) {
                // Never overwrite a malformed store with partial data from a failed parse.
                log.error(
                        "Failed to parse task record store {}, aborting write to avoid data loss.",
                        rel,
                        e);
                return;
            }
            record.touch();
            map.put(record.getTaskId(), record);
            persistTaskMap(rc, rel, map);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reads a single {@link TaskRecord} by task ID, or {@link Optional#empty()} if not found.
     */
    /**
     * 按任务 ID 读取单个 {@link TaskRecord}，如果未找到则返回 {@link Optional#empty()}。
     */
    public Optional<TaskRecord> readTaskRecord(
            RuntimeContext rc, String agentId, String sessionId, String taskId) {
        if (agentId == null
                || agentId.isBlank()
                || sessionId == null
                || sessionId.isBlank()
                || taskId == null
                || taskId.isBlank()) {
            return Optional.empty();
        }
        String rel = taskRecordPath(agentId, sessionId);
        Map<String, TaskRecord> map = readTaskMapLocked(rc, rel);
        return Optional.ofNullable(map.get(taskId));
    }

    /**
     * Returns all {@link TaskRecord}s for the given agent and session, in insertion order.
     */
    /**
     * 返回指定代理和会话的所有 {@link TaskRecord}，按插入顺序排列。
     */
    public Collection<TaskRecord> listTaskRecords(
            RuntimeContext rc, String agentId, String sessionId) {
        if (agentId == null || agentId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }
        String rel = taskRecordPath(agentId, sessionId);
        return List.copyOf(readTaskMapLocked(rc, rel).values());
    }

    /**
     * Returns all {@link TaskRecord}s for the given agent across <em>all</em> sessions that have
     * been active within {@code recentWindow}, in no particular order.
     *
     * <p>Unions task JSON files from the local disk and the filesystem layer. Files whose last
     * modification time (from disk mtime or {@link FileInfo#modifiedAt()}) is known and older than
     * {@code recentWindow} are skipped: once all tasks in a session reach a terminal state the file
     * is never modified again, so stale files cannot contain orphaned tasks.
     *
     * <p>Used by the orphan-task sweeper in
     * {@link io.agentscope.harness.agent.subagent.task.WorkspaceTaskRepository} to bound the
     * number of files read per sweep cycle without missing any genuinely running tasks.
     *
     * @param agentId the parent agent identifier
     * @param recentWindow only consider files modified within this duration; files known to be
     *     older are assumed to contain only terminal tasks and are skipped
     */
    /**
     * 返回指定代理在 {@code recentWindow} 内活跃的<em>所有</em>会话中的所有
     * {@link TaskRecord}，无特定顺序。
     *
     * <p>合并来自本地磁盘和文件系统层的任务 JSON 文件。已知最后修改时间
     * （来自磁盘 mtime 或 {@link FileInfo#modifiedAt()}）且早于 {@code recentWindow}
     * 的文件将被跳过：一旦会话中的所有任务达到终止状态，文件将不再被修改，
     * 因此过期文件不可能包含孤立任务。
     *
     * <p>由 {@link io.agentscope.harness.agent.subagent.task.WorkspaceTaskRepository}
     * 中的孤立任务清理器使用，用于限制每次清理周期读取的文件数量，同时不遗漏
     * 任何真正运行中的任务。
     *
     * @param agentId      父代理标识符
     * @param recentWindow 仅考虑在此时间范围内修改的文件；已知更早的文件
     *                     假定仅包含终止状态的任务，因此被跳过
     */
    public Collection<TaskRecord> listAllTaskRecords(
            RuntimeContext rc, String agentId, Duration recentWindow) {
        if (agentId == null || agentId.isBlank()) {
            return Collections.emptyList();
        }
        Instant cutoff = Instant.now().minus(recentWindow);
        String tasksRelDir = AGENTS_DIR + "/" + agentId + "/" + TASKS_DIR;

        // workspace-relative path → Optional<Instant> last-modified (empty = mtime unknown)
        // 工作区相对路径 → Optional<Instant> 最后修改时间（空 = mtime 未知）
        Map<String, Optional<Instant>> relPaths = new LinkedHashMap<>();

        if (filesystem != null) {
            GlobResult glob = filesystem.glob(rc, "*.json", tasksRelDir);
            if (glob.isSuccess() && glob.matches() != null) {
                for (FileInfo fi : glob.matches()) {
                    if (fi.path() == null || fi.path().isBlank()) {
                        continue;
                    }
                    String rel = normalizeRelativePath(fi.path().trim());
                    Instant mtime = parseInstantQuiet(fi.modifiedAt());
                    relPaths.put(rel, Optional.ofNullable(mtime));
                }
            }
        }

        Path tasksDir = resolveRuntimeDataPath(rc, AGENTS_DIR + "/" + agentId + "/" + TASKS_DIR);
        if (Files.isDirectory(tasksDir)) {
            try (Stream<Path> stream = Files.list(tasksDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(
                                p -> {
                                    String rel = tasksRelDir + "/" + p.getFileName();
                                    if (!relPaths.containsKey(rel)) {
                                        relPaths.put(rel, Optional.ofNullable(diskMtime(p)));
                                    }
                                });
            } catch (IOException e) {
                log.warn("Failed to list task files for agent {}: {}", agentId, e.getMessage());
            }
        }

        List<TaskRecord> all = new ArrayList<>();
        for (Map.Entry<String, Optional<Instant>> entry : relPaths.entrySet()) {
            Optional<Instant> mtime = entry.getValue();
            // Skip only when mtime is known and clearly before the cutoff
            // 仅在 mtime 已知且明显在截止时间之前时跳过
            if (mtime.isPresent() && mtime.get().isBefore(cutoff)) {
                continue;
            }
            all.addAll(readTaskMapLocked(rc, entry.getKey()).values());
        }
        return all;
    }

    private static Instant parseInstantQuiet(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant diskMtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads the timestamp written by the most recent successful orphan-sweep for this agent, or
     * {@link Optional#empty()} if no sweep has been recorded yet.
     *
     * <p>Stored in {@code agents/<agentId>/tasks/_sweep.marker} as a plain ISO-8601 string. Any
     * node can write to this path, so it naturally propagates through the shared filesystem layer.
     */
    /**
     * 读取此代理最近一次成功孤立任务清理写入的时间戳，如果尚未记录清理，
     * 则返回 {@link Optional#empty()}。
     *
     * <p>以纯 ISO-8601 字符串存储在 {@code agents/<agentId>/tasks/_sweep.marker} 中。
     * 任何节点都可以写入此路径，因此它会通过共享文件系统层自然传播。
     */
    public Optional<Instant> readSweepMarker(RuntimeContext rc, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        String rel = sweepMarkerPath(agentId);
        String content = readWritableWorkspaceRelativeUtf8(rc, rel);
        return Optional.ofNullable(parseInstantQuiet(content == null ? null : content.strip()));
    }

    /**
     * Records the current timestamp as the completion time of the most recent orphan-sweep for
     * this agent. Subsequent nodes that read this marker within the sweep interval will skip their
     * own sweep, reducing redundant workspace I/O in multi-node deployments.
     */
    /**
     * 将当前时间戳记录为此代理最近一次孤立任务清理的完成时间。
     * 在清理间隔内读取此标记的后续节点将跳过自身的清理，
     * 从而减少多节点部署中的冗余工作区 I/O。
     */
    public void writeSweepMarker(RuntimeContext rc, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        String rel = sweepMarkerPath(agentId);
        try {
            writeUtf8WorkspaceRelative(rc, rel, Instant.now().toString());
        } catch (Exception e) {
            log.warn("Failed to write sweep marker for agent {}: {}", agentId, e.getMessage());
        }
    }

    private String sweepMarkerPath(String agentId) {
        return AGENTS_DIR + "/" + agentId + "/" + TASKS_DIR + "/_sweep.marker";
    }

    private String taskRecordPath(String agentId, String sessionId) {
        return AGENTS_DIR + "/" + agentId + "/" + TASKS_DIR + "/" + sessionId + ".json";
    }

    /**
     * Acquires the per-file lock before delegating to {@link #readTaskMap(String)}, so that reads
     * are mutually exclusive with the read-modify-write cycle in {@link #writeTaskRecord}. This
     * prevents a concurrent writer's non-atomic file update (truncate → write) from being observed
     * as a partial JSON read.
     */
    /**
     * 在委托给 {@link #readTaskMap(String)} 之前获取每文件锁，使得读取与
     * {@link #writeTaskRecord} 中的读取-修改-写入循环互斥。这防止了并发写入者的
     * 非原子文件更新（截断→写入）被观察到为不完整的 JSON 读取。
     */
    private Map<String, TaskRecord> readTaskMapLocked(RuntimeContext rc, String rel) {
        ReentrantLock lock = pathLocks.computeIfAbsent(rel, k -> new ReentrantLock());
        lock.lock();
        try {
            try {
                return readTaskMap(rc, rel);
            } catch (IOException e) {
                // Surface corruption loudly, but do not mutate or reinitialize the backing file.
                log.error(
                        "Failed to parse task record store {}, returning empty in-memory view.",
                        rel,
                        e);
                return Collections.emptyMap();
            }
        } finally {
            lock.unlock();
        }
    }

    private Map<String, TaskRecord> readTaskMap(RuntimeContext rc, String rel) throws IOException {
        String json = readWritableWorkspaceRelativeUtf8(rc, rel);
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, TaskRecord> map = TASK_RECORD_JSON.readValue(json, TASK_MAP_TYPE);
        return map != null ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
    }

    private void persistTaskMap(RuntimeContext rc, String rel, Map<String, TaskRecord> map) {
        try {
            String serialized =
                    TASK_RECORD_JSON.writerWithDefaultPrettyPrinter().writeValueAsString(map);
            writeUtf8WorkspaceRelative(rc, rel, serialized);
        } catch (IOException e) {
            log.warn("Failed to write task record store {}: {}", rel, e.getMessage());
        }
    }

    private ObjectNode parseSessionStoreOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return SESSION_STORE_JSON.createObjectNode();
        }
        try {
            var node = SESSION_STORE_JSON.readTree(json);
            if (node instanceof ObjectNode on) {
                return on;
            }
        } catch (IOException e) {
            log.warn("Corrupt or unreadable session store, reinitializing: {}", e.getMessage());
        }
        return SESSION_STORE_JSON.createObjectNode();
    }

    private ObjectNode ensureSessionsObject(ObjectNode root) {
        var n = root.get("sessions");
        if (n instanceof ObjectNode on) {
            return on;
        }
        ObjectNode fresh = SESSION_STORE_JSON.createObjectNode();
        root.set("sessions", fresh);
        return fresh;
    }

    private String readWritableWorkspaceRelativeUtf8(RuntimeContext rc, String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        if (normalized.isEmpty()) {
            return "";
        }
        return readWithOverride(rc, normalized);
    }

    /** Overwrites a workspace-relative UTF-8 file. All writes go through the filesystem. */
    /** 覆盖工作区相对的 UTF-8 文件。所有写入操作都通过文件系统。 */
    public void writeUtf8WorkspaceRelative(RuntimeContext rc, String relativePath, String content) {
        if (relativePath == null || content == null) {
            return;
        }
        String normalized = normalizeRelativePath(relativePath);
        if (normalized.isEmpty()) {
            return;
        }
        if (filesystem == null) {
            writeLocalFile(normalized, content);
            return;
        }
        filesystem.uploadFiles(
                rc, List.of(Map.entry(normalized, content.getBytes(StandardCharsets.UTF_8))));
        // Best-effort: record upload size in index (no local file to stat from)
        // 最佳努力：在索引中记录上传大小（无本地文件可获取状态）
        if (index != null) {
            index.upsert(normalized, content.getBytes(StandardCharsets.UTF_8).length, null);
        }
    }

    // ==================== Two-layer read/write helpers ====================
    // ==================== 双层读写辅助方法 ====================

    /**
     * Two-layer read: filesystem first (namespaced by {@link
     * NamespaceFactory}), local disk fallback.
     */
    /**
     * 双层读取：先文件系统（由 {@link NamespaceFactory} 命名空间化），再本地磁盘回退。
     */
    private String readWithOverride(RuntimeContext rc, String relativePath) {
        String fsContent = readTextThroughFilesystem(rc, relativePath);
        if (!fsContent.isEmpty()) {
            return fsContent;
        }
        return readFileQuietly(workspace.resolve(relativePath));
    }

    private String readFileQuietly(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return "";
        }
    }

    private String readTextThroughFilesystem(RuntimeContext rc, String filePath) {
        if (filesystem == null) {
            return "";
        }
        ReadResult r = filesystem.read(rc, filePath, 0, 0);
        if (!r.isSuccess() || r.fileData() == null) {
            return "";
        }
        String c = r.fileData().content();
        return c != null ? c : "";
    }

    private void appendLocalFile(String relativePath, String content) {
        Path local = workspace.resolve(relativePath).normalize();
        if (!local.startsWith(workspace)) {
            log.warn("Refusing to write outside workspace: {}", relativePath);
            return;
        }
        try {
            if (local.getParent() != null) {
                Files.createDirectories(local.getParent());
            }
            Files.writeString(
                    local,
                    content,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            if (index != null) {
                index.upsertFromLocalFile(relativePath, local);
            }
        } catch (IOException e) {
            log.warn("Failed to append {}: {}", local, e.getMessage());
        }
    }

    /**
     * Atomically overwrites a workspace-relative UTF-8 file on local disk.
     *
     * <p>The content is first written to a sibling temp file, then renamed over the target using
     * {@link StandardCopyOption#ATOMIC_MOVE} (best-effort; falls back to a plain move when the
     * underlying filesystem does not support atomic rename). This prevents concurrent readers from
     * observing a partially-written file.
     */
    /**
     * 原子性地覆盖本地磁盘上的工作区相对 UTF-8 文件。
     *
     * <p>内容首先写入同级临时文件，然后使用 {@link StandardCopyOption#ATOMIC_MOVE}
     * 重命名覆盖目标文件（最佳努力；当底层文件系统不支持原子重命名时回退到普通移动）。
     * 这防止了并发读取者观察到部分写入的文件。
     */
    private void writeLocalFile(String relativePath, String content) {
        Path local = workspace.resolve(relativePath).normalize();
        if (!local.startsWith(workspace)) {
            log.warn("Refusing to write outside workspace: {}", relativePath);
            return;
        }
        Path temp = local.resolveSibling(local.getFileName() + ".tmp." + UUID.randomUUID());
        try {
            if (local.getParent() != null) {
                Files.createDirectories(local.getParent());
            }
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        local,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, local, StandardCopyOption.REPLACE_EXISTING);
            }
            if (index != null) {
                index.upsertFromLocalFile(relativePath, local);
            }
        } catch (IOException e) {
            log.warn("Failed to write {}: {}", local, e.getMessage());
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String s = relativePath.replace('\\', '/').stripLeading();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    /**
     * Returns workspace-relative paths of all memory files ({@code MEMORY.md} and {@code
     * memory/*.md}). Unions results from the {@link AbstractFilesystem} layer and the local disk,
     * deduplicating by relative path.
     */
    /**
     * 返回所有记忆文件（{@code MEMORY.md} 和 {@code memory/*.md}）的工作区相对路径。
     * 合并来自 {@link AbstractFilesystem} 层和本地磁盘的结果，按相对路径去重。
     */
    public List<String> listMemoryFilePaths(RuntimeContext rc) {
        Set<String> paths = new LinkedHashSet<>();

        if (filesystem != null) {
            ReadResult memMd = filesystem.read(rc, MEMORY_MD, 0, 1);
            if (memMd.isSuccess()) {
                paths.add(MEMORY_MD);
            }
            GlobResult glob = filesystem.glob(rc, "*.md", MEMORY_DIR);
            if (glob.isSuccess() && glob.matches() != null) {
                for (FileInfo fi : glob.matches()) {
                    if (fi.path() != null && !fi.path().isBlank()) {
                        String rel = normalizeRelativePath(fi.path().trim());
                        if (!rel.isEmpty()) {
                            paths.add(rel);
                        }
                    }
                }
            }
        }

        if (Files.isRegularFile(resolveRuntimeDataPath(rc, MEMORY_MD))) {
            paths.add(MEMORY_MD);
        }
        Path memDir = getMemoryDir(rc);
        if (Files.isDirectory(memDir)) {
            try (Stream<Path> walk = Files.list(memDir)) {
                walk.filter(p -> p.toString().endsWith(".md"))
                        .filter(Files::isRegularFile)
                        .forEach(p -> paths.add(MEMORY_DIR + "/" + p.getFileName()));
            } catch (IOException e) {
                log.warn("Failed to list memory dir: {}", e.getMessage());
            }
        }
        return new ArrayList<>(paths);
    }

    /**
     * Lists workspace-relative paths of all session log files ({@code *.log.jsonl}).
     * Unions results from the {@link AbstractFilesystem} layer and the local disk.
     */
    /**
     * 列出所有会话日志文件（{@code *.log.jsonl}）的工作区相对路径。
     * 合并来自 {@link AbstractFilesystem} 层和本地磁盘的结果。
     */
    public List<String> listSessionLogFiles(RuntimeContext rc) {
        Set<String> paths = new LinkedHashSet<>();

        if (filesystem != null) {
            GlobResult glob = filesystem.glob(rc, "*.log.jsonl", AGENTS_DIR);
            if (glob.isSuccess() && glob.matches() != null) {
                for (FileInfo fi : glob.matches()) {
                    if (fi.path() != null && !fi.path().isBlank()) {
                        String rel = normalizeRelativePath(fi.path().trim());
                        if (!rel.isEmpty()) {
                            paths.add(rel);
                        }
                    }
                }
            }
        }

        Path agentsDir = resolveRuntimeDataPath(rc, AGENTS_DIR);
        if (Files.isDirectory(agentsDir)) {
            try (Stream<Path> walk = Files.walk(agentsDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(WorkspaceConstants.SESSION_LOG_EXT))
                        .forEach(
                                p -> {
                                    String rel =
                                            agentsDir
                                                    .getParent()
                                                    .relativize(p.normalize())
                                                    .toString()
                                                    .replace('\\', '/');
                                    paths.add(rel);
                                });
            } catch (IOException e) {
                log.warn("Failed to list session log files: {}", e.getMessage());
            }
        }
        return new ArrayList<>(paths);
    }

    /** Workspace-relative path for indexing. */
    /** 用于索引的工作区相对路径。 */
    public String toWorkspaceRelativeString(Path absoluteUnderWorkspace) {
        return workspace
                .relativize(absoluteUnderWorkspace.normalize())
                .toString()
                .replace('\\', '/');
    }
}
