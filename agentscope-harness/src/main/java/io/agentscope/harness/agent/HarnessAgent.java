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
package io.agentscope.harness.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.state.StatePersistence;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.BakedContextFilesystem;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystemWithShell;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import io.agentscope.harness.agent.filesystem.sandbox.SandboxBackedFilesystem;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.hook.AgentTraceHook;
import io.agentscope.harness.agent.hook.CompactionHook;
import io.agentscope.harness.agent.hook.DynamicSkillHook;
import io.agentscope.harness.agent.hook.DynamicSubagentsHook;
import io.agentscope.harness.agent.hook.MemoryFlushHook;
import io.agentscope.harness.agent.hook.MemoryMaintenanceHook;
import io.agentscope.harness.agent.hook.SandboxLifecycleHook;
import io.agentscope.harness.agent.hook.SessionPersistenceHook;
import io.agentscope.harness.agent.hook.SubagentsHook;
import io.agentscope.harness.agent.hook.SubagentsHook.SubagentEntry;
import io.agentscope.harness.agent.hook.ToolResultEvictionHook;
import io.agentscope.harness.agent.hook.WorkspaceContextHook;
import io.agentscope.harness.agent.memory.MemoryConsolidator;
import io.agentscope.harness.agent.memory.MemoryFlushManager;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.sandbox.SandboxContext;
import io.agentscope.harness.agent.sandbox.SandboxDistributedOptions;
import io.agentscope.harness.agent.sandbox.SandboxExecutionGuard;
import io.agentscope.harness.agent.sandbox.SandboxManager;
import io.agentscope.harness.agent.sandbox.SandboxStateStore;
import io.agentscope.harness.agent.sandbox.SessionSandboxStateStore;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.session.WorkspaceSession;
import io.agentscope.harness.agent.skill.FilesystemBackedSkillRepository;
import io.agentscope.harness.agent.store.NamespaceFactory;
import io.agentscope.harness.agent.subagent.AgentSpecLoader;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.subagent.RemoteSubagentStub;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.SubagentFactory;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import io.agentscope.harness.agent.subagent.task.DefaultTaskRepository;
import io.agentscope.harness.agent.subagent.task.TaskRepository;
import io.agentscope.harness.agent.subagent.task.WorkspaceTaskRepository;
import io.agentscope.harness.agent.tool.FilesystemTool;
import io.agentscope.harness.agent.tool.MemoryGetTool;
import io.agentscope.harness.agent.tool.MemorySearchTool;
import io.agentscope.harness.agent.tool.SessionSearchTool;
import io.agentscope.harness.agent.tool.ShellExecuteTool;
import io.agentscope.harness.agent.tools.McpServerRegistrar;
import io.agentscope.harness.agent.tools.ToolFilter;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.tools.ToolsConfigLoader;
import io.agentscope.harness.agent.workspace.WorkspaceIndex;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HarnessAgent is the user-facing API that wraps {@link ReActAgent} with enhanced harness practices:
 *
 * <ul>
 *   <li>Workspace-based context loading (AGENTS.md, KNOWLEDGE.md)
 *   <li>Skill loading via optional {@link AgentSkillRepository}, else {@link FileSystemSkillRepository} on
 *       workspace/skills/
 *   <li>Subagent orchestration via task/task_output tools (sync + background)
 *   <li>Memory flush and message offload before context compression
 *   <li>Session environment initialization (OS, date, workspace info)
 *   <li>Pluggable file-system backend (local, sandbox, composite)
 *   <li>Memory search/get tools
 * </ul>
 *
 * <p>Advanced users can skip individual built-in tools or hooks via {@link HarnessAgent.Builder#disableFilesystemTools()},
 * {@link HarnessAgent.Builder#disableShellTool()}, {@link HarnessAgent.Builder#disableMemoryTools()},
 * {@link HarnessAgent.Builder#disableMemoryHooks()}, {@link HarnessAgent.Builder#disableSessionPersistence()},
 * {@link HarnessAgent.Builder#disableWorkspaceContext()}, and {@link HarnessAgent.Builder#disableSubagents()},
 * then register replacements on the {@link Toolkit} or {@link Hook} list.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * HarnessAgent agent = HarnessAgent.builder()
 *     .name("MyAgent")
 *     .model(model) // or .model("openai:gpt-5.5") via {@link ModelRegistry}
 *     .sysPrompt("You are a helpful assistant.")
 *     .workspace("/path/to/workspace")
 *     .build();
 *
 * Msg response = agent.call(
 *     Msg.userMsg("Hello!"),
 *     RuntimeContext.builder().sessionId("sess-1").build()
 * ).block();
 * }</pre>
 */
/**
 * HarnessAgent 是用户面向的 API，它使用增强的 harness 实践包装 {@link ReActAgent}：
 *
 * <ul>
 *   <li>基于工作区的上下文加载（AGENTS.md、KNOWLEDGE.md）
 *   <li>通过可选的 {@link AgentSkillRepository} 加载技能，否则使用 {@link FileSystemSkillRepository}
 *       从 workspace/skills/ 加载
 *   <li>通过 task/task_output 工具进行子代理编排（同步 + 后台）
 *   <li>上下文压缩前的记忆刷新和消息卸载
 *   <li>会话环境初始化（操作系统、日期、工作区信息）
 *   <li>可插拔的文件系统后端（本地、沙箱、复合）
 *   <li>记忆搜索/获取工具
 * </ul>
 *
 * <p>高级用户可以通过 {@link HarnessAgent.Builder#disableFilesystemTools()}、
 * {@link HarnessAgent.Builder#disableShellTool()}、{@link HarnessAgent.Builder#disableMemoryTools()}、
 * {@link HarnessAgent.Builder#disableMemoryHooks()}、{@link HarnessAgent.Builder#disableSessionPersistence()}、
 * {@link HarnessAgent.Builder#disableWorkspaceContext()} 和 {@link HarnessAgent.Builder#disableSubagents()}
 * 跳过单个内置工具或钩子，然后在 {@link Toolkit} 或 {@link Hook} 列表上注册替代品。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * HarnessAgent agent = HarnessAgent.builder()
 *     .name("MyAgent")
 *     .model(model) // 或通过 {@link ModelRegistry} 使用 .model("openai:gpt-5.5")
 *     .sysPrompt("你是一个有帮助的助手。")
 *     .workspace("/path/to/workspace")
 *     .build();
 *
 * Msg response = agent.call(
 *     Msg.userMsg("Hello!"),
 *     RuntimeContext.builder().sessionId("sess-1").build()
 * ).block();
 * }</pre>
 */
public class HarnessAgent implements Agent, StateModule, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgent.class);

    private final ReActAgent delegate;
    private final WorkspaceManager workspaceManager;
    private final CompactionHook compactionHook;
    private final Session defaultSession;
    private final SandboxContext defaultSandboxContext;
    private final List<AgentSkillRepository> skillRepositories;

    /**
     * SQLite-backed workspace index allocated during {@link Builder#build()} when the agent is
     * configured with a {@link io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec},
     * shared across the main {@link #workspaceManager} and any per-ctx views produced by
     * {@link #workspaceFor(String, String)}. Owned by this agent; released by {@link #close()}.
     * {@code null} when no index was created.
     */
    /**
     * 当代理配置了 {@link io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec}
     * 时，在 {@link Builder#build()} 期间分配的 SQLite 支持的工作区索引，
     * 在主要 {@link #workspaceManager} 和 {@link #workspaceFor(String, String)} 产生的
     * 每个按上下文视图之间共享。由此代理拥有；由 {@link #close()} 释放。
     * 当未创建索引时为 {@code null}。
     */
    private final WorkspaceIndex ownedWorkspaceIndex;

    /**
     * Factory for ctx-bound {@link WorkspaceManager} views — see {@link #workspaceFor(String,
     * String)}. Captured at build time so per-call views can be produced without depending on
     * mutable shared state on the agent instance.
     */
    /**
     * 绑定上下文的 {@link WorkspaceManager} 视图的工厂——参见 {@link #workspaceFor(String,
     * String)}。在构建时捕获，以便无需依赖代理实例上的可变共享状态即可生成
     * 每次调用的视图。
     */
    private final java.util.function.BiFunction<String, String, WorkspaceManager> workspaceFactory;

    /**
     * Factory for per-userId {@link WorkspaceSession} views. Used to bake the calling userId into
     * the {@link io.agentscope.harness.agent.store.NamespaceFactory} so that
     * {@link io.agentscope.core.session.JsonSession#getSessionDir} (which has no
     * {@link RuntimeContext} on the API surface) still produces a per-user path. Returns {@code
     * null} when the default session is not a {@link WorkspaceSession}; callers must fall back to
     * {@link #defaultSession}.
     */
    /**
     * 按 userId 的 {@link WorkspaceSession} 视图的工厂。用于将调用方的 userId 烘焙到
     * {@link io.agentscope.harness.agent.store.NamespaceFactory} 中，使得
     * {@link io.agentscope.core.session.JsonSession#getSessionDir}（其 API 表面没有
     * {@link RuntimeContext}）仍然生成按用户的路径。当默认会话不是 {@link WorkspaceSession}
     * 时返回 {@code null}；调用方必须回退到 {@link #defaultSession}。
     */
    private final java.util.function.Function<String, Session> sessionFactory;

    private volatile RuntimeContext runtimeContext;

    private HarnessAgent(
            ReActAgent delegate,
            WorkspaceManager workspaceManager,
            CompactionHook compactionHook,
            Session defaultSession,
            SandboxContext defaultSandboxContext,
            List<AgentSkillRepository> skillRepositories,
            java.util.function.BiFunction<String, String, WorkspaceManager> workspaceFactory,
            java.util.function.Function<String, Session> sessionFactory,
            WorkspaceIndex ownedWorkspaceIndex) {
        this.delegate = delegate;
        this.workspaceManager = workspaceManager;
        this.compactionHook = compactionHook;
        this.defaultSession = defaultSession;
        this.defaultSandboxContext = defaultSandboxContext;
        this.skillRepositories =
                skillRepositories != null ? List.copyOf(skillRepositories) : List.of();
        this.workspaceFactory = workspaceFactory;
        this.sessionFactory = sessionFactory;
        this.ownedWorkspaceIndex = ownedWorkspaceIndex;
    }

    /**
     * Releases resources owned by this agent — currently the SQLite-backed
     * {@link WorkspaceIndex} created when {@code RemoteFilesystemSpec} is configured.
     *
     * <p>Required for tests using {@code @TempDir} on Windows: while the JDBC connection holds
     * a file handle on {@code .index/workspace.db}, Windows refuses to delete the temp directory
     * and JUnit fails extension cleanup. Calling close releases the handle.
     *
     * <p>After close, the agent and any {@link WorkspaceManager} views produced from it must
     * not be used.
     */
    /**
     * 释放此代理拥有的资源——当前为在配置了 {@code RemoteFilesystemSpec} 时创建的
     * SQLite 支持的 {@link WorkspaceIndex}。
     *
     * <p>对于在 Windows 上使用 {@code @TempDir} 的测试是必需的：当 JDBC 连接持有着
     * {@code .index/workspace.db} 的文件句柄时，Windows 拒绝删除临时目录，
     * 导致 JUnit 扩展清理失败。调用 close 释放句柄。
     *
     * <p>关闭后，代理及其产生的任何 {@link WorkspaceManager} 视图都不能再使用。
     */
    @Override
    public void close() {
        if (ownedWorkspaceIndex != null) {
            ownedWorkspaceIndex.close();
        }
    }

    /** Calls the agent with a runtime context, which provides sessionId and other metadata. */
    /** 使用运行时上下文调用代理，该上下文提供 sessionId 和其他元数据。 */
    public Mono<Msg> call(Msg msg, RuntimeContext ctx) {
        return call(List.of(msg), ctx);
    }

    /** Calls the agent with multiple messages and a runtime context. */
    /** 使用多条消息和运行时上下文调用代理。 */
    public Mono<Msg> call(List<Msg> msgs, RuntimeContext ctx) {
        bindRuntimeContext(ctx);
        return delegate.call(msgs, coreForDelegate())
                .onErrorResume(
                        e -> {
                            if (isContextOverflowError(e)) {
                                return recoverFromOverflow(msgs);
                            }
                            return Mono.error(e);
                        });
    }

    /** Streams the agent response with a runtime context. */
    /** 使用运行时上下文流式传输代理响应。 */
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options, RuntimeContext ctx) {
        bindRuntimeContext(ctx);
        return delegate.stream(msgs, options, coreForDelegate());
    }

    /** Streams with default {@link StreamOptions} and a runtime context. */
    /** 使用默认 {@link StreamOptions} 和运行时上下文进行流式传输。 */
    public Flux<Event> stream(List<Msg> msgs, RuntimeContext ctx) {
        return stream(msgs, StreamOptions.defaults(), ctx);
    }

    /** Streams a single message with default {@link StreamOptions} and a runtime context. */
    /** 使用默认 {@link StreamOptions} 和运行时上下文流式传输单条消息。 */
    public Flux<Event> stream(Msg msg, RuntimeContext ctx) {
        return stream(List.of(msg), ctx);
    }

    private RuntimeContext coreForDelegate() {
        return runtimeContext != null ? runtimeContext : RuntimeContext.empty();
    }

    private Mono<Msg> recoverFromOverflow(List<Msg> msgs) {
        if (compactionHook != null) {
            // Force a compaction of the current memory contents by lowering the trigger threshold
            // to 1 so that compactIfNeeded always fires.
            log.warn(
                    "Context overflow detected, triggering emergency compaction via"
                            + " CompactionHook");
            return forceCompactAndRetry(delegate.getMemory(), msgs);
        }
        return Mono.error(
                new RuntimeException(
                        "Context overflow: no compaction configured, unable to recover"));
    }

    private Mono<Msg> forceCompactAndRetry(Memory memory, List<Msg> msgs) {
        List<Msg> allMsgs = memory.getMessages();
        if (allMsgs.isEmpty()) {
            return Mono.error(
                    new RuntimeException("Context overflow: memory is empty, cannot compact"));
        }
        RuntimeContext ctx = this.runtimeContext;
        String agentId = delegate.getName();
        String sessionId =
                ctx != null && ctx.getSessionId() != null ? ctx.getSessionId() : "default";

        // Force trigger by using a config with threshold=1 (always compact)
        // 通过使用阈值为 1 的配置强制触发（始终压缩）
        CompactionConfig forceConfig = CompactionConfig.builder().triggerMessages(1).build();
        MemoryFlushManager fm = new MemoryFlushManager(workspaceManager, delegate.getModel());
        ConversationCompactor compactor = new ConversationCompactor(delegate.getModel(), fm);

        return compactor
                .compactIfNeeded(coreRuntimeForRecovery(), allMsgs, forceConfig, agentId, sessionId)
                .flatMap(
                        opt -> {
                            if (opt.isPresent()) {
                                memory.clear();
                                for (Msg m : opt.get()) {
                                    memory.addMessage(m);
                                }
                                return delegate.call(msgs, coreRuntimeForRecovery());
                            }
                            return Mono.error(
                                    new RuntimeException(
                                            "Context overflow: emergency compaction yielded no"
                                                    + " result"));
                        });
    }

    private io.agentscope.core.agent.RuntimeContext coreRuntimeForRecovery() {
        return runtimeContext != null
                ? runtimeContext
                : io.agentscope.core.agent.RuntimeContext.empty();
    }

    private static boolean isContextOverflowError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("context_length_exceeded")
                || lower.contains("context length")
                || lower.contains("maximum context")
                || lower.contains("token limit")
                || lower.contains("too many tokens")
                || lower.contains("exceeds the model's maximum")
                || lower.contains("reduce the length");
    }

    private void bindRuntimeContext(RuntimeContext ctx) {
        if (ctx == null) {
            this.runtimeContext = null;
            return;
        }
        RuntimeContext effective = ensureSessionDefaults(ctx);
        this.runtimeContext = effective;
        if (effective.getSession() != null && effective.getSessionKey() != null) {
            try {
                delegate.loadIfExists(effective.getSession(), effective.getSessionKey());
            } catch (Exception e) {
                log.warn("Failed to load session state: {}", e.getMessage());
            }
        }
    }

    /**
     * Fills in default Session and SessionKey when the caller didn't provide them.
     * Session defaults to the agent-level {@link #defaultSession} (JsonSession).
     * SessionKey defaults to {@code SimpleSessionKey.of(sessionId)} when sessionId is
     * available, or {@code SimpleSessionKey.of(agentName)} as a last resort.
     */
    /**
     * 当调用方未提供默认 Session 和 SessionKey 时填充它们。
     * Session 默认为代理级别的 {@link #defaultSession} (JsonSession)。
     * 当 sessionId 可用时，SessionKey 默认为 {@code SimpleSessionKey.of(sessionId)}，
     * 否则最后回退到 {@code SimpleSessionKey.of(agentName)}。
     */
    private RuntimeContext ensureSessionDefaults(RuntimeContext ctx) {
        Session session = ctx.getSession();
        if (session == null) {
            // When the agent's default session is a WorkspaceSession (single-tenant local store),
            // produce a per-call view with the caller's userId baked into its NamespaceFactory so
            // session state lands under <workspace>/<userId>/agents/.../context/ instead of the
            // shared workspace root.
            // 当代理的默认会话是 WorkspaceSession（单租户本地存储）时，
            // 生成一个每次调用的视图，将调用方的 userId 烘焙到其 NamespaceFactory 中，
            // 使得会话状态落在 <workspace>/<userId>/agents/.../context/ 下，
            // 而不是共享的工作区根目录。
            String uid = ctx.getUserId();
            if (sessionFactory != null && uid != null && !uid.isBlank()) {
                Session perCall = sessionFactory.apply(uid);
                session = perCall != null ? perCall : defaultSession;
            } else {
                session = defaultSession;
            }
        }
        SessionKey sessionKey = ctx.getSessionKey();
        if (sessionKey == null) {
            String id = ctx.getSessionId();
            if (id != null && !id.isBlank()) {
                sessionKey = SimpleSessionKey.of(id);
            } else {
                sessionKey = SimpleSessionKey.of(delegate.getName());
            }
        }
        // Inject default sandbox context if the call doesn't provide one
        // 如果调用未提供默认沙箱上下文，则注入
        SandboxContext sandboxCtx =
                ctx.get(SandboxContext.class) != null
                        ? ctx.get(SandboxContext.class)
                        : defaultSandboxContext;

        if (session == ctx.getSession()
                && sessionKey == ctx.getSessionKey()
                && sandboxCtx == ctx.get(SandboxContext.class)) {
            return ctx;
        }
        return RuntimeContext.builder()
                .sessionId(ctx.getSessionId())
                .userId(ctx.getUserId())
                .session(session)
                .sessionKey(sessionKey)
                .putAll(ctx.getExtra())
                .put(SandboxContext.class, sandboxCtx)
                .build();
    }

    // ==================== Agent interface delegation ====================
    // ==================== 代理接口委托 ====================

    @Override
    public Mono<Msg> call(List<Msg> msgs) {
        return delegate.call(msgs);
    }

    @Override
    public Mono<Msg> call(List<Msg> msgs, Class<?> structuredModel) {
        return delegate.call(msgs, structuredModel);
    }

    @Override
    public Mono<Msg> call(List<Msg> msgs, JsonNode schema) {
        return delegate.call(msgs, schema);
    }

    @Override
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options) {
        return delegate.stream(msgs, options);
    }

    @Override
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel) {
        return delegate.stream(msgs, options, structuredModel);
    }

    @Override
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options, JsonNode schema) {
        return delegate.stream(msgs, options, schema);
    }

    @Override
    public Mono<Void> observe(Msg msg) {
        return delegate.observe(msg);
    }

    @Override
    public Mono<Void> observe(List<Msg> msgs) {
        return delegate.observe(msgs);
    }

    @Override
    public void interrupt() {
        delegate.interrupt();
    }

    @Override
    public void interrupt(Msg msg) {
        delegate.interrupt(msg);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getAgentId() {
        return delegate.getAgentId();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    public ReActAgent getDelegate() {
        return delegate;
    }

    public WorkspaceManager getWorkspaceManager() {
        return workspaceManager;
    }

    /**
     * Returns a {@link WorkspaceManager} view whose filesystem and namespace are bound to the
     * given {@code (userId, sessionId)} for the duration of the returned view's IO. Unlike
     * {@link #getWorkspaceManager()}, this does <strong>not</strong> mutate the shared {@link
     * RuntimeContext} state used by the chat path ({@link #call} / {@link #stream}) — so it is
     * safe to call concurrently from per-request controllers without racing with active chats or
     * other requests on the same agent.
     *
     * <p>Semantics by filesystem mode:
     *
     * <ul>
     *   <li><b>Remote (composite)</b> — a fresh composite filesystem is built whose per-route
     *       {@link io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem}s use the
     *       supplied {@code userId} / {@code sessionId} directly (no mutable reference). IO
     *       lands in {@code [agents, <agentId>, users, <userId>, <route>, ...]} per the
     *       configured {@link io.agentscope.harness.agent.IsolationScope}.
     *   <li><b>Local / Sandbox / Custom</b> — the existing shared filesystem is reused (these
     *       modes do not have a per-user race in their backend); only the workspace-relative
     *       namespace factory is rebound for the disk-fallback subtree.
     * </ul>
     *
     * <p>Pass {@code null} for either parameter to opt out of that dimension. The returned view
     * is lightweight and may be created per request; callers should not cache it across requests
     * with different users.
     */
    /**
     * 返回一个 {@link WorkspaceManager} 视图，其文件系统和命名空间在返回视图的
     * I/O 生命周期内绑定到指定的 {@code (userId, sessionId)}。与
     * {@link #getWorkspaceManager()} 不同，这<strong>不会</strong>改变聊道路径
     * （{@link #call} / {@link #stream}）使用的共享 {@link RuntimeContext} 状态——
     * 因此从按请求的控制器并发调用是安全的，不会与同一代理上的活动聊天或其他
     * 请求产生竞争。
     *
     * <p>按文件系统模式的语义：
     *
     * <ul>
     *   <li><b>远程（复合）</b>——构建一个新的复合文件系统，其每个路由的
     *       {@link io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem}
     *       直接使用提供的 {@code userId} / {@code sessionId}（无可变引用）。
     *       I/O 根据配置的 {@link io.agentscope.harness.agent.IsolationScope}
     *       落在 {@code [agents, <agentId>, users, <userId>, <route>, ...]} 下。
     *   <li><b>本地/沙箱/自定义</b>——重用现有的共享文件系统（这些模式的后端
     *       没有按用户竞争）；仅为磁盘回退子树重新绑定工作区相对命名空间工厂。
     * </ul>
     *
     * <p>为任一参数传递 {@code null} 以退出该维度。返回的视图是轻量级的，
     * 可以按请求创建；调用方不应在不同用户的请求之间缓存它。
     */
    public WorkspaceManager workspaceFor(String userId, String sessionId) {
        if (workspaceFactory == null) {
            return workspaceManager;
        }
        return workspaceFactory.apply(userId, sessionId);
    }

    /**
     * Returns the {@link CompactionHook} instance if compaction was configured, or {@code null}.
     * Exposed for testing to verify compaction mirroring in child agents.
     */
    /**
     * 如果配置了压缩，则返回 {@link CompactionHook} 实例，否则返回 {@code null}。
     * 暴露给测试以验证子代理中的压缩镜像。
     */
    public CompactionHook getCompactionHook() {
        return compactionHook;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    /**
     * Returns the ordered list of {@link AgentSkillRepository} instances bound to this agent.
     * The list reflects the four-layer composition (project-global → marketplace → workspace
     * shared → per-user namespace) computed at build time, in priority order from lowest to
     * highest. The returned list is immutable.
     */
    /**
     * 返回绑定到此代理的有序 {@link AgentSkillRepository} 实例列表。
     * 该列表反映了构建时计算的四层组合（项目全局 → 市场 → 工作区共享 → 按用户命名空间），
     * 按优先级从最低到最高排列。返回的列表是不可变的。
     */
    public List<AgentSkillRepository> getSkillRepositories() {
        return skillRepositories;
    }

    // ==================== StateModule delegation ====================
    // ==================== StateModule 委托 ====================

    @Override
    public void saveTo(Session session, SessionKey sessionKey) {
        delegate.saveTo(session, sessionKey);
    }

    @Override
    public void loadFrom(Session session, SessionKey sessionKey) {
        delegate.loadFrom(session, sessionKey);
    }

    @Override
    public boolean loadIfExists(Session session, SessionKey sessionKey) {
        return delegate.loadIfExists(session, sessionKey);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a {@link Builder} pre-populated with the observable properties of an existing
     * {@link ReActAgent}, making it easy to migrate to {@link HarnessAgent} with minimal changes.
     *
     * <p>The following properties are copied from {@code agent}:
     * <ul>
     *   <li>{@code name}, {@code description}, {@code sysPrompt}
     *   <li>{@code model}, {@code maxIters}, {@code generateOptions}
     *   <li>{@code planNotebook}
     *   <li>{@code toolkit} — a defensive copy; all custom tools are preserved, and
     *       HarnessAgent's built-in tools (filesystem, memory-search, etc.) are added on top unless
     *       disabled via {@link HarnessAgent.Builder#disableFilesystemTools()} and related {@code disable*}
     *       methods
     * </ul>
     *
     * <p>Properties that are intentionally <b>not</b> copied:
     * <ul>
     *   <li>{@code memory} — HarnessAgent always manages its own fresh in-memory conversation
     *       store backed by workspace persistence
     *   <li>hooks — already compiled into the existing agent and not accessible via public API;
     *       add new harness hooks via {@link Builder#hook(Hook)} if needed
     *   <li>long-term memory, RAG, statePersistence, structuredOutputReminder — not
     *       accessible via public API on a built agent; re-configure via the returned builder
     * </ul>
     *
     * <p>Example migration:
     * <pre>{@code
     * // Before
     * ReActAgent agent = ReActAgent.builder()
     *     .name("my-agent")
     *     .model(model)
     *     .toolkit(myToolkit)
     *     .build();
     *
     * // After — minimal change
     * HarnessAgent agent = HarnessAgent.from(existingReActAgent)
     *     .workspace("/my/workspace")
     *     .build();
     * }</pre>
     *
     * @param agent the existing {@link ReActAgent} to migrate; must not be null
     * @return a new {@link Builder} pre-populated with the agent's observable configuration
     */
    /**
     * 创建一个预填充了现有 {@link ReActAgent} 可观察属性的 {@link Builder}，
     * 使得以最小更改迁移到 {@link HarnessAgent} 变得容易。
     *
     * <p>以下属性从 {@code agent} 复制：
     * <ul>
     *   <li>{@code name}、{@code description}、{@code sysPrompt}
     *   <li>{@code model}、{@code maxIters}、{@code generateOptions}
     *   <li>{@code planNotebook}
     *   <li>{@code toolkit} — 防御性副本；所有自定义工具都被保留，
     *       除非通过 {@link HarnessAgent.Builder#disableFilesystemTools()} 和相关
     *       {@code disable*} 方法禁用，否则 HarnessAgent 的内置工具（文件系统、
     *       记忆搜索等）会在其基础上添加
     * </ul>
     *
     * <p>有意<strong>不</strong>复制的属性：
     * <ul>
     *   <li>{@code memory} — HarnessAgent 始终管理自己的内存会话存储，
     *       由工作区持久化支持
     *   <li>hooks — 已编译到现有代理中，无法通过公共 API 访问；
     *       如果需要，通过 {@link Builder#hook(Hook)} 添加新的 harness 钩子
     *   <li>长期记忆、RAG、statePersistence、structuredOutputReminder —
     *       无法通过已构建代理的公共 API 访问；通过返回的构建器重新配置
     * </ul>
     *
     * <p>迁移示例：
     * <pre>{@code
     * // 之前
     * ReActAgent agent = ReActAgent.builder()
     *     .name("my-agent")
     *     .model(model)
     *     .toolkit(myToolkit)
     *     .build();
     *
     * // 之后 — 最小更改
     * HarnessAgent agent = HarnessAgent.from(existingReActAgent)
     *     .workspace("/my/workspace")
     *     .build();
     * }</pre>
     *
     * @param agent 要迁移的现有 {@link ReActAgent}；不能为 null
     * @return 预填充了代理可观察配置的新 {@link Builder}
     */
    public static Builder from(ReActAgent agent) {
        Builder b = new Builder();
        b.name = agent.getName();
        b.description = agent.getDescription();
        b.sysPrompt = agent.getSysPrompt();
        b.model = agent.getModel();
        b.maxIters = agent.getMaxIters();
        b.generateOptions = agent.getGenerateOptions();
        b.planNotebook = agent.getPlanNotebook();
        // Defensive copy so HarnessAgent's build() does not mutate the original agent's toolkit
        // 防御性副本，以便 HarnessAgent 的 build() 不会改变原始代理的工具包
        b.toolkit = agent.getToolkit().copy();
        return b;
    }

    public static class Builder {

        // Core ReActAgent params
        // 核心 ReActAgent 参数
        private String name;
        private String agentId;
        private String description;
        private String sysPrompt;
        private Model model;
        private Toolkit toolkit = new Toolkit();
        private int maxIters = 15;
        private ExecutionConfig modelExecutionConfig;
        private ExecutionConfig toolExecutionConfig;
        private GenerateOptions generateOptions;
        private final List<Hook> hooks = new ArrayList<>();

        /**
         * Marketplace / external skill repositories layered between the project-global directory
         * and the workspace agent-shared directory. Empty by default. {@link
         * #skillRepository(AgentSkillRepository)} appends to this list.
         */
        /**
         * 市场/外部技能存储库，位于项目全局目录和工作区代理共享目录之间。
         * 默认为空。{@link #skillRepository(AgentSkillRepository)} 追加到此列表。
         */
        private final List<AgentSkillRepository> skillRepositories = new ArrayList<>();

        /**
         * Optional project-global skills directory (lowest precedence in the composition).
         * When {@code null}, no project-global layer is added.
         */
        /**
         * 可选的项目全局技能目录（组合中优先级最低）。
         * 当为 {@code null} 时，不添加项目全局层。
         */
        private Path projectGlobalSkillsDir;

        private ToolExecutionContext toolExecutionContext;

        // Long-term memory configuration
        // 长期记忆配置
        private LongTermMemory longTermMemory;
        private LongTermMemoryMode longTermMemoryMode = LongTermMemoryMode.BOTH;
        private boolean longTermMemoryAsyncRecord = false;

        // Plan configuration
        // 计划配置
        private PlanNotebook planNotebook;

        // RAG configuration
        // RAG 配置
        private final List<Knowledge> knowledgeBases = new ArrayList<>();
        private RAGMode ragMode = RAGMode.GENERIC;
        private RetrieveConfig retrieveConfig =
                RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build();

        // Additional delegate params
        // 额外的委托参数
        private StatePersistence statePersistence;
        private StructuredOutputReminder structuredOutputReminder;
        private boolean enableMetaTool = false;
        private boolean enablePendingToolRecovery = false;
        private boolean checkRunning = true;

        // Harness-specific params
        // Harness 特定参数
        private Path workspace;
        private String environmentMemory;
        private AbstractFilesystem abstractFilesystem;
        private Session session;
        private SandboxDistributedOptions sandboxDistributedOptions;

        /**
         * When {@code true}, this agent is a leaf worker (spawned subagent): it does not register
         * {@link SubagentsHook}, preventing recursive delegation. Main agents keep this {@code
         * false}.
         */
        /**
         * 当为 {@code true} 时，此代理是叶子工作节点（派生的子代理）：它不注册
         * {@link SubagentsHook}，防止递归委托。主代理保持此值为 {@code false}。
         */
        private boolean leafSubagent = false;

        /**
         * When {@code true} (default), registers {@link AgentTraceHook} to log reasoning and tool
         * execution at INFO; set logger {@code io.agentscope.harness.agent.hook.AgentTraceHook} to
         * DEBUG for full args and results. When {@code false}, no trace hook is added.
         */
        /**
         * 当为 {@code true}（默认值）时，注册 {@link AgentTraceHook} 在 INFO 级别记录
         * 推理和工具执行；将日志记录器 {@code io.agentscope.harness.agent.hook.AgentTraceHook}
         * 设置为 DEBUG 以获取完整参数和结果。当为 {@code false} 时，不添加跟踪钩子。
         */
        private boolean agentTracingLogEnabled = true;

        /**
         * When non-null, enables {@link CompactionHook} with this configuration.
         * Set via {@link #compaction(CompactionConfig)}.
         */
        /**
         * 当非 null 时，使用此配置启用 {@link CompactionHook}。
         * 通过 {@link #compaction(CompactionConfig)} 设置。
         */
        private CompactionConfig compactionConfig = null;

        /**
         * When non-null, enables {@link ToolResultEvictionHook} with this configuration.
         * Set via {@link #toolResultEviction(ToolResultEvictionConfig)}.
         */
        /**
         * 当非 null 时，使用此配置启用 {@link ToolResultEvictionHook}。
         * 通过 {@link #toolResultEviction(ToolResultEvictionConfig)} 设置。
         */
        private ToolResultEvictionConfig toolResultEvictionConfig = null;

        private final List<SubagentDeclaration> subagentDeclarations = new ArrayList<>();
        private final List<SubagentFactoryEntry> customSubagentFactories = new ArrayList<>();
        private TaskRepository taskRepository;
        private Object externalSubagentTool;
        private Function<String, Model> modelResolver;
        private final List<String> additionalContextFiles = new ArrayList<>();
        private int maxContextTokens = 8000;
        private boolean useLegacyXmlWorkspaceContext = false;

        /** When {@code true}, {@link FilesystemTool} is not registered. */
        /** 当为 {@code true} 时，不注册 {@link FilesystemTool}。 */
        private boolean disableFilesystemTools = false;

        /** When {@code true}, {@link ShellExecuteTool} is not registered (sandbox / local-shell modes only). */
        /** 当为 {@code true} 时，不注册 {@link ShellExecuteTool}（仅沙箱/本地 shell 模式）。 */
        private boolean disableShellTool = false;

        /**
         * When {@code true}, {@link MemorySearchTool}, {@link MemoryGetTool}, and {@link SessionSearchTool}
         * are not registered.
         */
        /**
         * 当为 {@code true} 时，不注册 {@link MemorySearchTool}、{@link MemoryGetTool}
         * 和 {@link SessionSearchTool}。
         */
        private boolean disableMemoryTools = false;

        /**
         * When {@code true}, {@link MemoryFlushHook} and {@link MemoryMaintenanceHook} are not registered.
         */
        /**
         * 当为 {@code true} 时，不注册 {@link MemoryFlushHook} 和 {@link MemoryMaintenanceHook}。
         */
        private boolean disableMemoryHooks = false;

        /** When {@code true}, {@link SessionPersistenceHook} is not registered. */
        /** 当为 {@code true} 时，不注册 {@link SessionPersistenceHook}。 */
        private boolean disableSessionPersistence = false;

        /** When {@code true}, {@link WorkspaceContextHook} is not registered. */
        /** 当为 {@code true} 时，不注册 {@link WorkspaceContextHook}。 */
        private boolean disableWorkspaceContext = false;

        /**
         * When {@code true}, {@link SubagentsHook} is not registered on this agent. Spawned leaf
         * subagents omit this hook regardless.
         */
        /**
         * 当为 {@code true} 时，此代理上不注册 {@link SubagentsHook}。
         * 派生的叶子子代理无论何值都省略此钩子。
         */
        private boolean disableSubagents = false;

        /**
         * When {@code true}, the dynamic skill hook ({@code DynamicSkillHook}) is not registered
         * even when a workspace filesystem is configured. The build falls back to the legacy
         * {@code SkillHook} path via {@code resolveSkillBox()}.
         */
        /**
         * 当为 {@code true} 时，即使配置了工作区文件系统，也不注册动态技能钩子
         * ({@code DynamicSkillHook})。构建回退到通过 {@code resolveSkillBox()} 的
         * 旧版 {@code SkillHook} 路径。
         */
        private boolean disableDynamicSkills = false;

        /**
         * When {@code true}, the dynamic subagents hook ({@code DynamicSubagentsHook}) is not
         * registered even when a workspace filesystem is configured. The build falls back to the
         * legacy {@link SubagentsHook} which scans subagent declarations once at build time.
         */
        /**
         * 当为 {@code true} 时，即使配置了工作区文件系统，也不注册动态子代理钩子
         * ({@code DynamicSubagentsHook})。构建回退到旧版 {@link SubagentsHook}，
         * 后者在构建时扫描一次子代理声明。
         */
        private boolean disableDynamicSubagents = false;

        /**
         * When {@code true}, {@code workspace/tools.json} is not consulted at build time. MCP
         * servers and allow/deny lists from that file are skipped entirely; the toolkit keeps
         * exactly the built-ins registered programmatically.
         */
        /**
         * 当为 {@code true} 时，构建时不查阅 {@code workspace/tools.json}。
         * 完全跳过该文件中的 MCP 服务器和允许/拒绝列表；
         * 工具包仅保留通过编程方式注册的内置工具。
         */
        private boolean disableToolsConfig = false;

        /**
         * Programmatic override for {@code workspace/tools.json}. When non-null, {@link
         * ToolsConfigLoader} is bypassed and this value is used directly. Useful for tests.
         */
        /**
         * 对 {@code workspace/tools.json} 的编程覆盖。当非 null 时，绕过
         * {@link ToolsConfigLoader} 并直接使用此值。对测试有用。
         */
        private ToolsConfig toolsConfigOverride;

        // Filesystem mode configuration (at most one of these three is set)
        // 文件系统模式配置（最多设置这三个中的一个）
        private SandboxFilesystemSpec sandboxFilesystemSpec;
        private RemoteFilesystemSpec remoteFilesystemSpec;
        private LocalFilesystemSpec localFilesystemSpec;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the stable identifier used as the agent's namespace key in the composite filesystem
         * (e.g. {@code [agents, <agentId>, users, <userId>, ...]}). This is distinct from
         * {@link #name(String)}, which is the human-facing display name and may change without
         * rewriting any keys.
         *
         * <p>When unset, {@code build()} falls back to {@link #name(String)} for the namespace key,
         * preserving prior behavior. Callers that need rename-safe storage (e.g. multi-tenant
         * platforms whose agents have a stable catalog/URL id distinct from the display name)
         * should set this explicitly.
         */
        /**
         * 设置在复合文件系统中用作代理命名空间键的稳定标识符
         * （例如 {@code [agents, <agentId>, users, <userId>, ...]}）。
         * 这与 {@link #name(String)} 不同，后者是人面向的显示名称，
         * 可以在不重写任何键的情况下更改。
         *
         * <p>当未设置时，{@code build()} 回退到使用 {@link #name(String)} 作为命名空间键，
         * 保持先前行为。需要重命名安全存储的调用方（例如多租户平台，
         * 其代理具有与显示名称不同的稳定目录/URL ID）应显式设置此值。
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder sysPrompt(String sysPrompt) {
            this.sysPrompt = sysPrompt;
            return this;
        }

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        /**
         * Configures the model from a string id resolved via {@link ModelRegistry}: a named
         * registration ({@link ModelRegistry#register(String, Model)}) or a built-in pattern such
         * as {@code openai:gpt-5.5}, {@code dashscope:qwen-max}, {@code anthropic:claude-sonnet-4-5},
         * {@code gemini:gemini-2.0-flash}, or {@code ollama:llama3}. API keys for auto-created models
         * come from standard environment variables ({@code OPENAI_API_KEY}, {@code DASHSCOPE_API_KEY},
         * etc.).
         *
         * @param modelId registry id or {@code provider:model} string
         * @return this builder
         * @throws IllegalArgumentException if the id cannot be resolved
         */
        /**
         * 通过 {@link ModelRegistry} 解析的字符串 ID 配置模型：已命名的注册
         * ({@link ModelRegistry#register(String, Model)}) 或内置模式，
         * 例如 {@code openai:gpt-5.5}、{@code dashscope:qwen-max}、
         * {@code anthropic:claude-sonnet-4-5}、{@code gemini:gemini-2.0-flash}
         * 或 {@code ollama:llama3}。自动创建的模型的 API 密钥来自标准环境变量
         * ({@code OPENAI_API_KEY}、{@code DASHSCOPE_API_KEY} 等)。
         *
         * @param modelId 注册表 ID 或 {@code provider:model} 字符串
         * @return 此构建器
         * @throws IllegalArgumentException 如果 ID 无法解析
         */
        public Builder model(String modelId) {
            this.model = ModelRegistry.resolve(modelId);
            return this;
        }

        public Builder toolkit(Toolkit toolkit) {
            this.toolkit = toolkit;
            return this;
        }

        public Builder maxIters(int maxIters) {
            this.maxIters = maxIters;
            return this;
        }

        public Builder modelExecutionConfig(ExecutionConfig config) {
            this.modelExecutionConfig = config;
            return this;
        }

        public Builder toolExecutionConfig(ExecutionConfig config) {
            this.toolExecutionConfig = config;
            return this;
        }

        public Builder generateOptions(GenerateOptions options) {
            this.generateOptions = options;
            return this;
        }

        public Builder hook(Hook hook) {
            this.hooks.add(hook);
            return this;
        }

        public Builder hooks(List<Hook> hooks) {
            this.hooks.addAll(hooks);
            return this;
        }

        /**
         * Adds a marketplace / external skill repository (e.g. {@code GitSkillRepository},
         * Nacos, HTTP). Repositories compose <em>additively</em> with workspace skills: the
         * default precedence is project-global → marketplace repos (in registration order) →
         * workspace agent-shared → per-user namespaced filesystem, with later layers
         * overriding earlier ones on name collisions. Call this method multiple times to add
         * multiple sources.
         */
        /**
         * 添加一个市场/外部技能存储库（例如 {@code GitSkillRepository}、Nacos、HTTP）。
         * 存储库与工作区技能<em>相加</em>组合：默认优先级为项目全局 → 市场存储库
         * （按注册顺序）→ 工作区代理共享 → 按用户命名空间文件系统，
         * 后一层在名称冲突时覆盖前一层。多次调用此方法以添加多个来源。
         */
        public Builder skillRepository(AgentSkillRepository skillRepository) {
            if (skillRepository != null) {
                this.skillRepositories.add(skillRepository);
            }
            return this;
        }

        /**
         * Replaces the current marketplace repository list with the given collection. Useful
         * for bulk configuration from an external config; equivalent to clearing the list and
         * calling {@link #skillRepository(AgentSkillRepository)} for each entry.
         */
        /**
         * 用给定集合替换当前市场存储库列表。对于从外部配置批量配置很有用；
         * 等同于清空列表并为每个条目调用 {@link #skillRepository(AgentSkillRepository)}。
         */
        public Builder skillRepositories(List<AgentSkillRepository> repositories) {
            this.skillRepositories.clear();
            if (repositories != null) {
                for (AgentSkillRepository repo : repositories) {
                    if (repo != null) {
                        this.skillRepositories.add(repo);
                    }
                }
            }
            return this;
        }

        /**
         * Configures a project-global skills directory layered <em>below</em> marketplace and
         * workspace skills (lowest precedence). Used to ship default skills shared across all
         * agents started from a project. Pass {@code null} to clear.
         */
        /**
         * 配置一个项目全局技能目录，层级<em>低于</em>市场和工作区技能
         * （最低优先级）。用于提供从项目启动的所有代理共享的默认技能。
         * 传递 {@code null} 以清除。
         */
        public Builder projectGlobalSkillsDir(Path projectGlobalSkillsDir) {
            this.projectGlobalSkillsDir = projectGlobalSkillsDir;
            return this;
        }

        public Builder toolExecutionContext(ToolExecutionContext ctx) {
            this.toolExecutionContext = ctx;
            return this;
        }

        /**
         * Adds a knowledge base for RAG (Retrieval-Augmented Generation) on the delegate
         * {@link ReActAgent}.
         *
         * @param knowledge the knowledge base to add
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上添加用于 RAG（检索增强生成）的知识库。
         *
         * @param knowledge 要添加的知识库
         * @return 此构建器实例
         */
        public Builder knowledge(Knowledge knowledge) {
            if (knowledge != null) {
                this.knowledgeBases.add(knowledge);
            }
            return this;
        }

        /**
         * Adds multiple knowledge bases for RAG (Retrieval-Augmented Generation) on the delegate
         * {@link ReActAgent}.
         *
         * @param knowledges the list of knowledge bases to add
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上添加多个用于 RAG（检索增强生成）的知识库。
         *
         * @param knowledges 要添加的知识库列表
         * @return 此构建器实例
         */
        public Builder knowledges(List<Knowledge> knowledges) {
            if (knowledges != null) {
                this.knowledgeBases.addAll(knowledges);
            }
            return this;
        }

        /**
         * Sets the RAG mode on the delegate {@link ReActAgent}.
         *
         * @param mode the RAG mode (GENERIC, AGENTIC, or NONE)
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置 RAG 模式。
         *
         * @param mode RAG 模式（GENERIC、AGENTIC 或 NONE）
         * @return 此构建器实例
         */
        public Builder ragMode(RAGMode mode) {
            if (mode != null) {
                this.ragMode = mode;
            }
            return this;
        }

        /**
         * Sets the retrieve configuration for RAG on the delegate {@link ReActAgent}.
         *
         * @param config the retrieve configuration
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置 RAG 的检索配置。
         *
         * @param config 检索配置
         * @return 此构建器实例
         */
        public Builder retrieveConfig(RetrieveConfig config) {
            if (config != null) {
                this.retrieveConfig = config;
            }
            return this;
        }

        /**
         * Sets the {@link PlanNotebook} for plan-based task execution on the delegate
         * {@link ReActAgent}.
         *
         * <p>Plan management tools will be automatically registered to the toolkit and a hook
         * will be added to inject plan hints before each reasoning step.
         *
         * @param planNotebook the configured PlanNotebook instance
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置基于计划的任务执行的 {@link PlanNotebook}。
         *
         * <p>计划管理工具将自动注册到工具包，并将添加一个钩子以在每个推理步骤之前
         * 注入计划提示。
         *
         * @param planNotebook 配置的 PlanNotebook 实例
         * @return 此构建器实例
         */
        public Builder planNotebook(PlanNotebook planNotebook) {
            this.planNotebook = planNotebook;
            return this;
        }

        /**
         * Enables plan functionality with default configuration on the delegate
         * {@link ReActAgent}. Equivalent to {@code planNotebook(PlanNotebook.builder().build())}.
         *
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上使用默认配置启用计划功能。
         * 等同于 {@code planNotebook(PlanNotebook.builder().build())}。
         *
         * @return 此构建器实例
         */
        public Builder enablePlan() {
            this.planNotebook = PlanNotebook.builder().build();
            return this;
        }

        /**
         * Sets the long-term memory for the delegate {@link ReActAgent}.
         *
         * @param longTermMemory the long-term memory implementation
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置长期记忆。
         *
         * @param longTermMemory 长期记忆实现
         * @return 此构建器实例
         */
        public Builder longTermMemory(LongTermMemory longTermMemory) {
            this.longTermMemory = longTermMemory;
            return this;
        }

        /**
         * Sets the long-term memory mode for the delegate {@link ReActAgent}.
         *
         * @param mode the long-term memory mode
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置长期记忆模式。
         *
         * @param mode 长期记忆模式
         * @return 此构建器实例
         */
        public Builder longTermMemoryMode(LongTermMemoryMode mode) {
            if (mode != null) {
                this.longTermMemoryMode = mode;
            }
            return this;
        }

        /**
         * Sets whether long-term memory recording should be performed asynchronously on the
         * delegate {@link ReActAgent}.
         *
         * @param asyncRecord whether to record memories asynchronously
         * @return this builder instance
         */
        /**
         * 设置是否应在委托 {@link ReActAgent} 上异步执行长期记忆记录。
         *
         * @param asyncRecord 是否异步记录记忆
         * @return 此构建器实例
         */
        public Builder longTermMemoryAsyncRecord(boolean asyncRecord) {
            this.longTermMemoryAsyncRecord = asyncRecord;
            return this;
        }

        /**
         * Sets the state persistence configuration for the delegate {@link ReActAgent}.
         *
         * @param statePersistence the state persistence configuration
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置状态持久化配置。
         *
         * @param statePersistence 状态持久化配置
         * @return 此构建器实例
         */
        public Builder statePersistence(StatePersistence statePersistence) {
            this.statePersistence = statePersistence;
            return this;
        }

        /**
         * Sets the structured output enforcement mode for the delegate {@link ReActAgent}.
         *
         * @param reminder the structured output reminder mode
         * @return this builder instance
         */
        /**
         * 在委托 {@link ReActAgent} 上设置结构化输出强制模式。
         *
         * @param reminder 结构化输出提醒模式
         * @return 此构建器实例
         */
        public Builder structuredOutputReminder(StructuredOutputReminder reminder) {
            this.structuredOutputReminder = reminder;
            return this;
        }

        /**
         * Enables or disables the meta-tool functionality for the delegate {@link ReActAgent}.
         *
         * @param enableMetaTool true to enable the meta-tool
         * @return this builder instance
         */
        /**
         * 启用或禁用委托 {@link ReActAgent} 的元工具功能。
         *
         * @param enableMetaTool true 启用元工具
         * @return 此构建器实例
         */
        public Builder enableMetaTool(boolean enableMetaTool) {
            this.enableMetaTool = enableMetaTool;
            return this;
        }

        /**
         * Enables or disables automatic recovery from orphaned pending tool calls on the delegate
         * {@link ReActAgent}.
         *
         * @param enable true to enable auto-recovery
         * @return this builder instance
         */
        /**
         * 启用或禁用委托 {@link ReActAgent} 上从孤立待处理工具调用的自动恢复。
         *
         * @param enable true 启用自动恢复
         * @return 此构建器实例
         */
        public Builder enablePendingToolRecovery(boolean enable) {
            this.enablePendingToolRecovery = enable;
            return this;
        }

        /**
         * Enables or disables the concurrent-execution guard on the delegate
         * {@link ReActAgent}. Defaults to {@code true}.
         *
         * @param checkRunning true to enable the guard
         * @return this builder instance
         */
        /**
         * 启用或禁用委托 {@link ReActAgent} 上的并发执行保护。默认为 {@code true}。
         *
         * @param checkRunning true 启用保护
         * @return 此构建器实例
         */
        public Builder checkRunning(boolean checkRunning) {
            this.checkRunning = checkRunning;
            return this;
        }

        /**
         * Sets the workspace directory. Pass {@code null} to use the default
         * {@code ${cwd}/.agentscope/workspace}.
         *
         * @see #workspace(String)
         */
        /**
         * 设置工作区目录。传递 {@code null} 以使用默认值
         * {@code ${cwd}/.agentscope/workspace}。
         *
         * @see #workspace(String)
         */
        public Builder workspace(Path workspace) {
            this.workspace = workspace;
            return this;
        }

        /**
         * Sets the workspace directory from a filesystem path string (resolved with
         * {@link Path#of(String, String...)}). Equivalent to {@link #workspace(Path)} with
         * {@code Path.of(path.strip())}.
         *
         * <p>Pass {@code null} for the same default as {@link #workspace(Path)} with a {@code null}
         * argument. Blank or whitespace-only strings are rejected.
         *
         * @param path absolute or relative path string, or {@code null} for the default workspace
         */
        /**
         * 通过文件系统路径字符串设置工作区目录（使用 {@link Path#of(String, String...)}
         * 解析）。等同于使用 {@code Path.of(path.strip())} 调用
         * {@link #workspace(Path)}。
         *
         * <p>传递 {@code null} 与使用 {@code null} 参数调用 {@link #workspace(Path)}
         * 具有相同的默认效果。空白或仅含空格的字符串将被拒绝。
         *
         * @param path 绝对或相对路径字符串，或 {@code null} 使用默认工作区
         */
        public Builder workspace(String path) {
            if (path == null) {
                this.workspace = null;
                return this;
            }
            String trimmed = path.strip();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("workspace path must not be blank");
            }
            this.workspace = Path.of(trimmed);
            return this;
        }

        public Builder environmentMemory(String environmentMemory) {
            this.environmentMemory = environmentMemory;
            return this;
        }

        /**
         * Escape hatch: sets a custom {@link AbstractFilesystem} implementation directly.
         *
         * <p>Prefer {@link #filesystem(LocalFilesystemSpec)}, {@link #filesystem(RemoteFilesystemSpec)}
         * or {@link #filesystem(SandboxFilesystemSpec)} unless you have a bespoke backend that is
         * not expressible via any of the declarative specs.
         */
        /**
         * 后门：直接设置自定义 {@link AbstractFilesystem} 实现。
         *
         * <p>除非您有无法通过任何声明性规范表达的定制后端，
         * 否则建议使用 {@link #filesystem(LocalFilesystemSpec)}、
         * {@link #filesystem(RemoteFilesystemSpec)} 或
         * {@link #filesystem(SandboxFilesystemSpec)}。
         */
        public Builder abstractFilesystem(AbstractFilesystem backend) {
            this.abstractFilesystem = backend;
            return this;
        }

        /**
         * Configures <b>Mode 2 — sandbox filesystem</b> mode: fully isolated workspace running in a
         * sandbox (for example Docker). Long-term memory extraction/read and shell execution are
         * all routed through the sandbox session. State can be persisted via snapshots and resumed
         * by the configured isolation scope.
         *
         * @param spec sandbox filesystem spec (for example Docker sandbox spec)
         * @return this builder
         */
        /**
         * 配置<b>模式 2 — 沙箱文件系统</b>模式：在沙箱（例如 Docker）中运行的
         * 完全隔离的工作区。长期记忆提取/读取和 shell 执行都通过沙箱会话路由。
         * 状态可以通过快照持久化，并由配置的隔离范围恢复。
         *
         * @param spec 沙箱文件系统规范（例如 Docker 沙箱规范）
         * @return 此构建器
         */
        public Builder filesystem(SandboxFilesystemSpec spec) {
            this.sandboxFilesystemSpec = spec;
            return this;
        }

        /**
         * Configures <b>Mode 1 — composite (non-sandbox) filesystem</b> mode: a unified workspace
         * view that blends a local {@code LocalFilesystem} backend with a shared
         * {@code RemoteFilesystem} for distributed long-term memory. Shell execution is not
         * available in this mode — selected prefixes ({@code MEMORY.md}, {@code memory/},
         * {@code agents/.../sessions/}) are routed to the store to keep memory consistent across
         * replicas.
         */
        /**
         * 配置<b>模式 1 — 复合（非沙箱）文件系统</b>模式：统一的工作区视图，
         * 将本地 {@code LocalFilesystem} 后端与共享的 {@code RemoteFilesystem}
         * 混合使用，用于分布式长期记忆。此模式下不提供 Shell 执行——
         * 选定的前缀（{@code MEMORY.md}、{@code memory/}、{@code agents/.../sessions/}）
         * 被路由到存储以保持副本间的记忆一致性。
         */
        public Builder filesystem(RemoteFilesystemSpec spec) {
            this.remoteFilesystemSpec = spec;
            return this;
        }

        /**
         * Configures <b>Mode 3 — local filesystem with shell</b> mode: the agent workspace is a
         * plain local directory and shell commands execute on the host. Long-term memory is kept
         * on the same local disk. Use for single-process / single-replica deployments.
         */
        /**
         * 配置<b>模式 3 — 带 Shell 的本地文件系统</b>模式：代理工作区是一个
         * 普通的本地目录，shell 命令在主机上执行。长期记忆保存在同一本地磁盘上。
         * 用于单进程/单副本部署。
         */
        public Builder filesystem(LocalFilesystemSpec spec) {
            this.localFilesystemSpec = spec;
            return this;
        }

        /**
         * Enables or disables agent execution trace logging via {@link AgentTraceHook}.
         * Default is {@code true}.
         */
        /**
         * 启用或禁通过 {@link AgentTraceHook} 的代理执行跟踪日志记录。
         * 默认为 {@code true}。
         */
        public Builder enableAgentTracingLog(boolean enabled) {
            this.agentTracingLogEnabled = enabled;
            return this;
        }

        /**
         * Skips registration of {@link FilesystemTool} ({@code read_file}, {@code write_file}, etc.).
         * Use when supplying a custom filesystem tool or a stricter wrapper on the {@link Toolkit}.
         */
        /**
         * 跳过 {@link FilesystemTool}（{@code read_file}、{@code write_file} 等）的注册。
         * 在提供自定义文件系统工具或 {@link Toolkit} 上更严格的包装器时使用。
         */
        public Builder disableFilesystemTools() {
            this.disableFilesystemTools = true;
            return this;
        }

        /**
         * Skips registration of {@link ShellExecuteTool}. Only applies when the resolved filesystem is an
         * {@link AbstractSandboxFilesystem} (sandbox mode or default local workspace with shell).
         */
        /**
         * 跳过 {@link ShellExecuteTool} 的注册。仅在解析的文件系统是
         * {@link AbstractSandboxFilesystem}（沙箱模式或带 Shell 的默认本地工作区）时适用。
         */
        public Builder disableShellTool() {
            this.disableShellTool = true;
            return this;
        }

        /**
         * Disables dynamic per-call skill loading from the workspace filesystem. Forces the build
         * to use the legacy {@code resolveSkillBox()} path even when a workspace filesystem is
         * configured.
         */
        /**
         * 禁用从工作区文件系统动态加载每次调用的技能。强制构建使用旧版
         * {@code resolveSkillBox()} 路径，即使配置了工作区文件系统。
         */
        public Builder disableDynamicSkills() {
            this.disableDynamicSkills = true;
            return this;
        }

        /**
         * Disables dynamic per-call subagent reload from the workspace filesystem. Forces the
         * build to use the legacy {@link SubagentsHook} which materialises the entry list once at
         * build time.
         */
        /**
         * 禁用从工作区文件系统动态重新加载每次调用的子代理。强制构建使用旧版
         * {@link SubagentsHook}，后者在构建时一次性地物化条目列表。
         */
        public Builder disableDynamicSubagents() {
            this.disableDynamicSubagents = true;
            return this;
        }

        /**
         * Skips registration of {@link MemorySearchTool}, {@link MemoryGetTool}, and {@link SessionSearchTool}.
         */
        /**
         * 跳过 {@link MemorySearchTool}、{@link MemoryGetTool} 和 {@link SessionSearchTool} 的注册。
         */
        public Builder disableMemoryTools() {
            this.disableMemoryTools = true;
            return this;
        }

        /**
         * Skips registration of {@link MemoryFlushHook} and {@link MemoryMaintenanceHook} (workspace-backed
         * memory maintenance around model calls).
         */
        /**
         * 跳过 {@link MemoryFlushHook} 和 {@link MemoryMaintenanceHook} 的注册
         * （模型调用前后的工作区支持的记忆维护）。
         */
        public Builder disableMemoryHooks() {
            this.disableMemoryHooks = true;
            return this;
        }

        /**
         * Skips registration of {@link SessionPersistenceHook}. Only use when you persist agent state
         * through another mechanism.
         */
        /**
         * 跳过 {@link SessionPersistenceHook} 的注册。仅当您通过其他机制持久化代理状态时使用。
         */
        public Builder disableSessionPersistence() {
            this.disableSessionPersistence = true;
            return this;
        }

        /**
         * Skips registration of {@link WorkspaceContextHook}, so AGENTS.md / workspace context is not
         * injected into the system message.
         */
        /**
         * 跳过 {@link WorkspaceContextHook} 的注册，使得 AGENTS.md / 工作区上下文
         * 不会注入到系统消息中。
         */
        public Builder disableWorkspaceContext() {
            this.disableWorkspaceContext = true;
            return this;
        }

        // ---- Sub-agent builder methods ----
        // ---- 子代理构建器方法 ----

        /**
         * When {@code true}, {@link SubagentsHook} is not registered on this agent. Spawned leaf
         * subagents omit this hook regardless.
         */
        /**
         * 当为 {@code true} 时，此代理上不注册 {@link SubagentsHook}。
         */
        public Builder disableSubagents() {
            this.disableSubagents = true;
            return this;
        }

        /**
         * When {@code true}, {@code workspace/tools.json} is not consulted at build time.
         */
        /**
         * 当为 {@code true} 时，构建时不查阅 {@code workspace/tools.json}。
         */
        public Builder disableToolsConfig() {
            this.disableToolsConfig = true;
            return this;
        }

        /**
         * Programmatic override for {@code workspace/tools.json}.
         */
        /**
         * 对 {@code workspace/tools.json} 的编程覆盖。
         */
        public Builder toolsConfigOverride(ToolsConfig toolsConfigOverride) {
            this.toolsConfigOverride = toolsConfigOverride;
            return this;
        }

        /**
         * Enables {@link CompactionHook} with the given config.
         */
        /**
         * 使用给定的配置启用 {@link CompactionHook}。
         */
        public Builder compaction(CompactionConfig config) {
            this.compactionConfig = config;
            return this;
        }

        /**
         * Enables {@link ToolResultEvictionHook} with the given config.
         */
        /**
         * 使用给定的配置启用 {@link ToolResultEvictionHook}。
         */
        public Builder toolResultEviction(ToolResultEvictionConfig config) {
            this.toolResultEvictionConfig = config;
            return this;
        }

        /**
         * Adds a subagent declaration. Equivalent to placing a {@code .md} file in the
         * {@code subagents/} directory; both sources are merged.
         */
        /**
         * 添加一个子代理声明。等同于在 {@code subagents/} 目录中放置一个
         * {@code .md} 文件；两个来源被合并。
         */
        public Builder subagent(SubagentDeclaration declaration) {
            if (declaration != null) {
                this.subagentDeclarations.add(declaration);
            }
            return this;
        }

        /**
         * Adds multiple subagent declarations at once.
         */
        /**
         * 一次添加多个子代理声明。
         */
        public Builder subagents(List<SubagentDeclaration> declarations) {
            if (declarations != null) {
                this.subagentDeclarations.addAll(declarations);
            }
            return this;
        }

        /**
         * Registers a custom factory for named subagents. When a subagent with the given
         * {@code name} is requested, the factory creates it instead of using the default
         * declaration-based path.
         */
        /**
         * 为已命名的子代理注册自定义工厂。当请求具有给定 {@code name} 的子代理时，
         * 使用此工厂创建，而不是使用默认的基于声明的路径。
         */
        public Builder customSubagentFactory(String name, Function<String, Agent> factory) {
            if (name != null && factory != null) {
                this.customSubagentFactories.add(new SubagentFactoryEntry(name, factory));
            }
            return this;
        }

        /**
         * Sets the task repository for subagent task management.
         */
        /**
         * 设置用于子代理任务管理的任务存储库。
         */
        public Builder taskRepository(TaskRepository taskRepository) {
            this.taskRepository = taskRepository;
            return this;
        }

        /**
         * Provides a custom external subagent tool instance.
         */
        /**
         * 提供一个自定义的外部子代理工具实例。
         */
        public Builder externalSubagentTool(Object externalSubagentTool) {
            this.externalSubagentTool = externalSubagentTool;
            return this;
        }

        /**
         * Sets the model resolver function for subagent model overrides.
         */
        /**
         * 设置用于子代理模型覆盖的模型解析器函数。
         */
        public Builder modelResolver(Function<String, Model> modelResolver) {
            this.modelResolver = modelResolver;
            return this;
        }

        /**
         * Adds an additional context file path relative to the workspace root.
         */
        /**
         * 添加相对于工作区根目录的额外上下文文件路径。
         */
        public Builder additionalContextFile(String relativePath) {
            if (relativePath != null && !relativePath.isBlank()) {
                this.additionalContextFiles.add(relativePath);
            }
            return this;
        }

        /**
         * Adds multiple additional context file paths.
         */
        /**
         * 添加多个额外上下文文件路径。
         */
        public Builder additionalContextFiles(List<String> relativePaths) {
            if (relativePaths != null) {
                for (String p : relativePaths) {
                    if (p != null && !p.isBlank()) {
                        this.additionalContextFiles.add(p);
                    }
                }
            }
            return this;
        }

        /**
         * Sets the maximum context tokens for workspace context injection.
         */
        /**
         * 设置工作区上下文注入的最大上下文 token 数。
         */
        public Builder maxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
            return this;
        }

        /**
         * Whether to use the legacy XML-style workspace context instead of Markdown.
         */
        /**
         * 是否使用旧版 XML 风格的工作区上下文而不是 Markdown。
         */
        public Builder useLegacyXmlWorkspaceContext(boolean useLegacy) {
            this.useLegacyXmlWorkspaceContext = useLegacy;
            return this;
        }

        /**
         * Sets a custom session implementation for the agent.
         */
        /**
         * 为代理设置自定义会话实现。
         */
        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        /**
         * Sets sandbox distributed options for multi-node sandbox coordination.
         */
        /**
         * 设置用于多节点沙箱协调的沙箱分布式选项。
         */
        public Builder sandboxDistributedOptions(SandboxDistributedOptions options) {
            this.sandboxDistributedOptions = options;
            return this;
        }

        /**
         * Convenience method for {@link #sandboxDistributedOptions(SandboxDistributedOptions)}.
         */
        /**
         * {@link #sandboxDistributedOptions(SandboxDistributedOptions)} 的便捷方法。
         */
        public Builder sandboxDistributed(SandboxDistributedOptions options) {
            return sandboxDistributedOptions(options);
        }

        // =================================================================
        //  BUILD
        // =================================================================

        /**
         * Builds the {@link HarnessAgent} from the current builder configuration.
         *
         * @return a fully-configured {@link HarnessAgent} instance
         */
        /**
         * 从当前构建器配置构建 {@link HarnessAgent}。
         *
         * @return 一个完全配置的 {@link HarnessAgent} 实例
         */
        public HarnessAgent build() {
            // ---- Workspace resolution ----
            Path resolvedWorkspace = workspace;
            if (resolvedWorkspace == null) {
                String cwd = System.getProperty("user.dir");
                resolvedWorkspace = Paths.get(cwd, WorkspaceConstants.DEFAULT_WORKSPACE_ROOT);
            }
            try {
                Files.createDirectories(resolvedWorkspace);
            } catch (Exception e) {
                log.warn(
                        "Failed to create workspace directory: {}",
                        e.getMessage());
            }

            // ---- Namespace factory from sandbox / remote spec ----
            NamespaceFactory nsFactory = null;
            if (sandboxFilesystemSpec != null) {
                nsFactory = sandboxFilesystemSpec.getNamespaceFactory();
            } else if (remoteFilesystemSpec != null) {
                nsFactory = remoteFilesystemSpec.getNamespaceFactory();
            }

            // ---- WorkspaceIndex (only for remote-fs mode) ----
            WorkspaceIndex workspaceIndex = null;
            if (remoteFilesystemSpec != null) {
                workspaceIndex = WorkspaceIndex.open(resolvedWorkspace);
            }

            // ---- WorkspaceManager ----
            WorkspaceManager wsManager = buildWorkspaceManager(
                    resolvedWorkspace, workspaceIndex, nsFactory);

            // ---- Resolve filesystem backend ----
            String agentNsKey = agentId != null && !agentId.isBlank() ? agentId : name;
            SandboxBackedFilesystem capturedSandboxFs = null;
            AbstractFilesystem filesystem = resolveFilesystem(
                    resolvedWorkspace, agentNsKey, workspaceIndex, nsFactory);
            if (filesystem instanceof SandboxBackedFilesystem sbfs) {
                capturedSandboxFs = sbfs;
            }

            // ---- Workspace factory (per-call view) ----
            java.util.function.BiFunction<String, String, WorkspaceManager> workspaceFactoryFn =
                    buildWorkspaceFactoryFn(
                            resolvedWorkspace, workspaceIndex, nsFactory, filesystem);

            // ---- Session ----
            Session effectiveSession = resolveSession(resolvedWorkspace, nsFactory);
            // ---- Session factory (per-user-id view) ----
            java.util.function.Function<String, Session> sessionFactoryFn =
                    buildSessionFactoryFn(resolvedWorkspace, nsFactory);

            // ---- Sandbox context ----
            SandboxContext defaultSandboxContext = resolveSandboxContext(
                    effectiveSession, resolvedWorkspace, filesystem);

            // ---- Hooks ----
            List<Hook> allHooks = new ArrayList<>(hooks);

            if (agentTracingLogEnabled) {
                allHooks.add(new AgentTraceHook());
            }

            if (compactionConfig != null) {
                CompactionHook compactionHook = new CompactionHook(
                        wsManager,
                        delegate.getModel(),
                        compactionConfig);
                allHooks.add(compactionHook);
            }

            if (toolResultEvictionConfig != null) {
                allHooks.add(new ToolResultEvictionHook(toolResultEvictionConfig));
            }

            if (!disableMemoryHooks) {
                allHooks.add(new MemoryFlushHook(wsManager));
                allHooks.add(new MemoryMaintenanceHook(wsManager));
            }

            if (!disableSessionPersistence) {
                allHooks.add(new SessionPersistenceHook(effectiveSession));
            }

            if (!disableWorkspaceContext) {
                WorkspaceContextHook ctxHook = new WorkspaceContextHook(
                        wsManager,
                        additionalContextFiles,
                        maxContextTokens,
                        useLegacyXmlWorkspaceContext);
                allHooks.add(ctxHook);
            }

            // ---- Subagents (non-leaf only) ----
            if (!leafSubagent && !disableSubagents && model != null) {
                if (filesystem != null && !disableDynamicSubagents) {
                    DynamicSubagentsHook dynamicSubagentsHook =
                            buildDynamicSubagentsHook(
                                    wsManager, resolvedWorkspace, capturedSandboxFs);
                    if (dynamicSubagentsHook != null) {
                        allHooks.add(dynamicSubagentsHook);
                    }
                } else {
                    SubagentsHook subagentsHook =
                            buildSubagentsHook(wsManager, resolvedWorkspace, capturedSandboxFs);
                    if (subagentsHook != null) {
                        allHooks.add(subagentsHook);
                    }
                }
            }

            // ---- Toolkit ----
            Toolkit agentToolkit = toolkit;

            if (!disableMemoryTools) {
                agentToolkit.registerTool(new MemorySearchTool(wsManager));
                agentToolkit.registerTool(new MemoryGetTool(wsManager));
                agentToolkit.registerTool(new SessionSearchTool(wsManager));
            }

            if (!disableFilesystemTools) {
                agentToolkit.registerTool(new FilesystemTool(filesystem));
            }

            if (!disableShellTool && filesystem instanceof AbstractSandboxFilesystem sandbox) {
                agentToolkit.registerTool(new ShellExecuteTool(sandbox));
            }

            // ---- workspace/tools.json: MCP servers + allow/deny filter ----
            ToolsConfig resolvedToolsConfig = null;
            if (!disableToolsConfig) {
                if (toolsConfigOverride != null) {
                    resolvedToolsConfig = toolsConfigOverride;
                } else if (wsManager != null) {
                    resolvedToolsConfig = ToolsConfigLoader.load(wsManager).orElse(null);
                }
            }
            if (resolvedToolsConfig != null) {
                McpServerRegistrar.register(agentToolkit, resolvedToolsConfig.getMcpServers());
            }

            // ---- Skills ----
            final AtomicReference<HarnessAgent> selfRef = new AtomicReference<>();
            Supplier<RuntimeContext> currentRcSupplier =
                    () -> {
                        HarnessAgent self = selfRef.get();
                        return self != null && self.runtimeContext != null
                                ? self.runtimeContext
                                : RuntimeContext.empty();
                    };
            List<AgentSkillRepository> orderedSkillRepos =
                    composeSkillRepositories(wsManager, filesystem, currentRcSupplier);
            SkillBox effectiveSkillBox = null;
            if (!orderedSkillRepos.isEmpty()) {
                if (disableDynamicSkills) {
                    effectiveSkillBox = staticSkillBoxFromRepos(orderedSkillRepos, agentToolkit);
                } else {
                    allHooks.add(new DynamicSkillHook(orderedSkillRepos, agentToolkit));
                }
            }

            // ---- Apply tools.json allow/deny filter ----
            if (resolvedToolsConfig != null) {
                ToolFilter.apply(agentToolkit, resolvedToolsConfig);
            }

            // ---- Build ReActAgent ----
            ReActAgent.Builder reactBuilder =
                    ReActAgent.builder()
                            .name(name)
                            .description(description)
                            .sysPrompt(sysPrompt)
                            .model(model)
                            .toolkit(agentToolkit)
                            .memory(InMemoryMemory.builder().build())
                            .maxIters(maxIters)
                            .hooks(allHooks);

            if (modelExecutionConfig != null) {
                reactBuilder.modelExecutionConfig(modelExecutionConfig);
            }
            if (toolExecutionConfig != null) {
                reactBuilder.toolExecutionConfig(toolExecutionConfig);
            }
            if (generateOptions != null) {
                reactBuilder.generateOptions(generateOptions);
            }
            if (effectiveSkillBox != null) {
                reactBuilder.skillBox(effectiveSkillBox);
            }
            if (toolExecutionContext != null) {
                reactBuilder.toolExecutionContext(toolExecutionContext);
            }
            if (!knowledgeBases.isEmpty()) {
                reactBuilder
                        .knowledges(knowledgeBases)
                        .ragMode(ragMode)
                        .retrieveConfig(retrieveConfig);
            }
            if (planNotebook != null) {
                reactBuilder.planNotebook(planNotebook);
            }
            if (longTermMemory != null) {
                reactBuilder
                        .longTermMemory(longTermMemory)
                        .longTermMemoryMode(longTermMemoryMode)
                        .longTermMemoryAsyncRecord(longTermMemoryAsyncRecord);
            }
            if (statePersistence != null) {
                reactBuilder.statePersistence(statePersistence);
            }
            if (structuredOutputReminder != null) {
                reactBuilder.structuredOutputReminder(structuredOutputReminder);
            }
            reactBuilder
                    .enableMetaTool(enableMetaTool)
                    .enablePendingToolRecovery(enablePendingToolRecovery)
                    .checkRunning(checkRunning);

            ReActAgent delegate = reactBuilder.build();

            log.info(
                    "HarnessAgent '{}' built [workspace={}, backend={}, subagents={}]",
                    name,
                    resolvedWorkspace,
                    filesystem.getClass().getSimpleName(),
                    !leafSubagent && !disableSubagents && model != null);

            HarnessAgent harnessAgent =
                    new HarnessAgent(
                            delegate,
                            wsManager,
                            null, // compactionHook is not stored for now
                            effectiveSession,
                            defaultSandboxContext,
                            orderedSkillRepos,
                            workspaceFactoryFn,
                            sessionFactoryFn,
                            workspaceIndex);
            selfRef.set(harnessAgent);
            return harnessAgent;
        }

        // ---- Build helpers ----
        // ---- 构建辅助方法 ----

        private WorkspaceManager buildWorkspaceManager(
                Path workspace, WorkspaceIndex index, NamespaceFactory nsFactory) {
            if (index != null) {
                return new WorkspaceManager(workspace, null, index, nsFactory);
            }
            return new WorkspaceManager(workspace);
        }

        private java.util.function.BiFunction<String, String, WorkspaceManager>
                buildWorkspaceFactoryFn(
                        Path resolvedWorkspace,
                        WorkspaceIndex workspaceIndex,
                        NamespaceFactory nsFactory,
                        AbstractFilesystem filesystem) {
            return (userId, sessionId) -> {
                WorkspaceManager view = new WorkspaceManager(
                        resolvedWorkspace, filesystem, workspaceIndex, nsFactory);
                return view;
            };
        }

        private java.util.function.Function<String, Session> buildSessionFactoryFn(
                Path workspace, NamespaceFactory nsFactory) {
            return userId -> {
                if (userId == null || userId.isBlank()) {
                    return null;
                }
                return new WorkspaceSession(workspace, name, nsFactory);
            };
        }

        private Session resolveSession(Path workspace, NamespaceFactory nsFactory) {
            if (session != null) {
                return session;
            }
            return new WorkspaceSession(workspace, name, nsFactory);
        }

        private SandboxContext resolveSandboxContext(
                Session session, Path workspace, AbstractFilesystem filesystem) {
            // Simplified sandbox context resolution
            return null;
        }

        // @formatter:off
        /**
         * Subagent context section injected into every subagent's system prompt.
         * Establishes identity, rules, output format, and prohibited behaviours for a leaf worker.
         * The task itself is delivered as the first user message, not duplicated here.
         */
        /**
         * 注入到每个子代理系统提示中的子代理上下文部分。
         * 建立叶子工作者的身份、规则、输出格式和禁止行为。
         * 任务本身作为第一条用户消息传递，不在此处重复。
         */
        private static final String SUBAGENT_CONTEXT_SECTION =
                """
                # Subagent Context

                You are a **subagent** spawned by the main agent for a specific task.

                ## Your Role
                - Complete the assigned task. That's your entire purpose.
                - You are NOT the main agent. Don't try to be.

                ## Rules
                1. **Stay focused** — Do your assigned task, nothing else
                2. **Complete the task** — Your final message will be automatically reported to the main agent
                3. **Don't initiate** — No heartbeats, no proactive actions, no side quests
                4. **Be ephemeral** — You may be terminated after task completion. That's fine.
                5. **Recover from truncated tool output** — If you see `[truncated: output exceeded context limit]`, re-read only what you need using smaller chunks (read with offset/limit, or targeted grep/head/tail) instead of full re-reads

                ## Output Format
                When complete, your final response should include:
                - What you accomplished or found
                - Any relevant details the main agent should know
                - Keep it concise but informative

                ## What You DON'T Do
                - NO user conversations (that's the main agent's job)
                - NO spawning further subagents — you are a leaf worker
                - NO pretending to be the main agent
                - Return plain text results; let the main agent deliver them to the user
                """;

        // @formatter:on

        private static final String GENERAL_PURPOSE_BASE_PROMPT =
                "You are a highly capable general-purpose subagent.";

        /**
         * Builds a system prompt for a subagent by appending {@link #SUBAGENT_CONTEXT_SECTION} to
         * the given base prompt. If the base is blank, only the context section is used.
         */
        /**
         * 通过将 {@link #SUBAGENT_CONTEXT_SECTION} 追加到给定的基础提示来构建子代理的系统提示。
         * 如果基础为空，则仅使用上下文部分。
         */
        private static String buildSubagentSysPrompt(String basePrompt) {
            String base =
                    (basePrompt != null && !basePrompt.isBlank()) ? basePrompt.stripTrailing() : "";
            return base.isEmpty()
                    ? SUBAGENT_CONTEXT_SECTION
                    : base + "\n\n" + SUBAGENT_CONTEXT_SECTION;
        }

        // -----------------------------------------------------------------
        //  Backend
        // -----------------------------------------------------------------

        private AbstractFilesystem resolveFilesystem(
                Path workspace,
                String agentId,
                WorkspaceIndex workspaceIndex,
                NamespaceFactory nsFactory) {
            if (abstractFilesystem != null) {
                return abstractFilesystem;
            }
            if (remoteFilesystemSpec != null) {
                if (workspaceIndex != null) {
                    remoteFilesystemSpec.workspaceIndex(workspaceIndex);
                }
                return remoteFilesystemSpec.toFilesystem(workspace, agentId, nsFactory);
            }
            if (localFilesystemSpec != null) {
                return localFilesystemSpec.toFilesystem(workspace, nsFactory);
            }
            // Default to Mode 3 with out-of-the-box LocalFilesystemWithShell settings.
            // 默认使用 Mode 3 的开箱即用 LocalFilesystemWithShell 设置。
            return new LocalFilesystemWithShell(workspace, nsFactory);
        }

        private void validateDistributedSandboxConfig(
                Session effectiveSession, SandboxContext sandboxContext) {
            if (sandboxFilesystemSpec.getSandboxStateStore() == null
                    && effectiveSession instanceof WorkspaceSession) {
                throw new IllegalStateException(
                        "filesystem(SandboxFilesystemSpec) requires a distributed Session backend"
                                + " (for example RedisSession) to persist and restore sandbox"
                                + " state across distributed instances."
                                + " Configure one via .session(...)."
                                + " For single-node use, opt out via"
                                + " .sandboxDistributed(SandboxDistributedOptions.builder()"
                                + ".requireDistributed(false).build()).");
            }
            if (sandboxContext == null
                    || sandboxContext.getSnapshotSpec() == null
                    || sandboxContext.getSnapshotSpec() instanceof NoopSnapshotSpec) {
                throw new IllegalStateException(
                        "filesystem(SandboxFilesystemSpec) requires a non-noop snapshotSpec to"
                                + " restore workspace archives across distributed instances."
                                + " Configure one via SandboxFilesystemSpec.snapshotSpec(...)."
                                + " For single-node use, opt out via"
                                + " .sandboxDistributed(SandboxDistributedOptions.builder()"
                                + ".requireDistributed(false).build()).");
            }
        }

        /**
         * Builds a {@link RuntimeContext} that bakes in the supplied {@code userId} and
         * {@code sessionId} for out-of-band IO performed via
         * {@link HarnessAgent#workspaceFor(String, String)}. Used together with
         * {@link BakedContextFilesystem} so the underlying namespace factories see this
         * identity regardless of what the caller passes downstream.
         */
        /**
         * 构建一个 {@link RuntimeContext}，将提供的 {@code userId} 和 {@code sessionId}
         * 烘焙到其中，用于通过 {@link HarnessAgent#workspaceFor(String, String)}
         * 执行的带外 IO。
         */
        private static RuntimeContext buildBakedRuntimeContext(String userId, String sessionId) {
            if ((userId == null || userId.isBlank())
                    && (sessionId == null || sessionId.isBlank())) {
                return RuntimeContext.empty();
            }
            RuntimeContext.Builder b = RuntimeContext.builder();
            if (userId != null && !userId.isBlank()) {
                b.userId(userId);
            }
            if (sessionId != null && !sessionId.isBlank()) {
                b.sessionId(sessionId);
            }
            return b.build();
        }

        // -----------------------------------------------------------------
        //  Subagents
        // -----------------------------------------------------------------

        private SubagentsHook buildSubagentsHook(
                WorkspaceManager wsManager, Path workspace, SandboxBackedFilesystem sandboxFs) {
            List<SubagentEntry> entries = buildSubagentEntries(workspace, sandboxFs);
            TaskRepository repo;
            if (taskRepository != null) {
                repo = taskRepository;
            } else if (wsManager != null) {
                String taskAgentId =
                        agentId != null && !agentId.isBlank()
                                ? agentId
                                : (name != null && !name.isBlank() ? name : "HarnessAgent");
                repo = new WorkspaceTaskRepository(wsManager, taskAgentId);
            } else {
                repo = new DefaultTaskRepository();
            }

            if (externalSubagentTool != null) {
                return new SubagentsHook(entries, externalSubagentTool, repo);
            }

            AbstractFilesystem fs = wsManager.getFilesystem();
            Function<SubagentDeclaration, SubagentFactory> factoryFn =
                    decl -> buildDeclaredFactory(decl, workspace, sandboxFs);
            return new SubagentsHook(entries, repo, wsManager, fs, workspace, factoryFn);
        }

        /**
         * Builds the {@link DynamicSubagentsHook} used by default when a workspace filesystem is
         * configured.
         */
        /**
         * 构建在配置了工作区文件系统时默认使用的 {@link DynamicSubagentsHook}。
         */
        private DynamicSubagentsHook buildDynamicSubagentsHook(
                WorkspaceManager wsManager, Path workspace, SandboxBackedFilesystem sandboxFs) {
            List<SubagentEntry> staticEntries = buildStaticSubagentEntries(workspace, sandboxFs);
            TaskRepository repo;
            if (taskRepository != null) {
                repo = taskRepository;
            } else if (wsManager != null) {
                String taskAgentId =
                        agentId != null && !agentId.isBlank()
                                ? agentId
                                : (name != null && !name.isBlank() ? name : "HarnessAgent");
                repo = new WorkspaceTaskRepository(wsManager, taskAgentId);
            } else {
                repo = new DefaultTaskRepository();
            }

            AbstractFilesystem fs = wsManager.getFilesystem();
            Function<SubagentDeclaration, SubagentFactory> factoryFn =
                    decl -> buildDeclaredFactory(decl, workspace, sandboxFs);
            DefaultAgentManager manager = new DefaultAgentManager(staticEntries, wsManager);
            return new DynamicSubagentsHook(
                    staticEntries, fs, workspace, factoryFn, manager, externalSubagentTool, repo);
        }

        /**
         * Like {@link #buildSubagentEntries(Path, SandboxBackedFilesystem)} but omits the
         * local-disk {@code subagents/} scan.
         */
        /**
         * 类似于 {@link #buildSubagentEntries(Path, SandboxBackedFilesystem)}，
         * 但省略了本地磁盘 {@code subagents/} 扫描。
         */
        private List<SubagentEntry> buildStaticSubagentEntries(
                Path resolvedWorkspace, SandboxBackedFilesystem sandboxFs) {
            List<SubagentEntry> entries = new ArrayList<>();

            entries.add(
                    new SubagentEntry(
                            "general-purpose",
                            "General-purpose subagent with same capabilities as the main agent.",
                            buildGeneralPurposeFactory(resolvedWorkspace, sandboxFs),
                            null));

            for (SubagentDeclaration decl : subagentDeclarations) {
                entries.add(
                        new SubagentEntry(
                                decl.getName(),
                                decl.getDescription(),
                                buildDeclaredFactory(decl, resolvedWorkspace, sandboxFs),
                                decl));
            }

            for (SubagentFactoryEntry custom : customSubagentFactories) {
                entries.add(
                        new SubagentEntry(
                                custom.name(),
                                custom.name(),
                                () -> custom.factory().apply(custom.name()),
                                null));
            }

            return entries;
        }

        /**
         * Builds a factory for the built-in general-purpose subagent.
         */
        /**
         * 为内置的通用子代理构建工厂。
         */
        private SubagentFactory buildGeneralPurposeFactory(
                Path workspace, SandboxBackedFilesystem sandboxFs) {
            final Model capturedModel = this.model;
            final Toolkit capturedParentToolkit =
                    this.toolkit != null ? this.toolkit.copy() : new Toolkit();
            final AbstractFilesystem capturedBackend =
                    sandboxFs != null ? sandboxFs : this.abstractFilesystem;
            final int capturedMaxIters = this.maxIters;
            final ExecutionConfig capturedModelExec = this.modelExecutionConfig;
            final ExecutionConfig capturedToolExec = this.toolExecutionConfig;
            final GenerateOptions capturedGenOpts = this.generateOptions;
            final String capturedEnvMemory = this.environmentMemory;
            final List<Hook> capturedHooks = List.copyOf(this.hooks);
            final List<AgentSkillRepository> capturedSkillRepos =
                    List.copyOf(this.skillRepositories);
            final Path capturedProjectGlobalSkillsDir = this.projectGlobalSkillsDir;
            final boolean capturedUseLegacyXmlWorkspaceContext = this.useLegacyXmlWorkspaceContext;
            final boolean capturedDisableFilesystemTools = this.disableFilesystemTools;
            final boolean capturedDisableShellTool = this.disableShellTool;
            final boolean capturedDisableMemoryTools = this.disableMemoryTools;
            final boolean capturedDisableMemoryHooks = this.disableMemoryHooks;
            final boolean capturedDisableSessionPersistence = this.disableSessionPersistence;
            final boolean capturedDisableWorkspaceContext = this.disableWorkspaceContext;
            final CompactionConfig capturedCompactionConfig = this.compactionConfig;
            final ToolResultEvictionConfig capturedToolResultEvictionConfig =
                    this.toolResultEvictionConfig;
            final boolean capturedAgentTracingLogEnabled = this.agentTracingLogEnabled;
            final List<String> capturedAdditionalContextFiles =
                    List.copyOf(this.additionalContextFiles);
            final int capturedMaxContextTokens = this.maxContextTokens;

            return () -> {
                Builder sub =
                        HarnessAgent.builder()
                                .name("general-purpose-subagent")
                                .description("General-purpose subagent for isolated task execution")
                                .sysPrompt(buildSubagentSysPrompt(null))
                                .model(capturedModel)
                                .toolkit(capturedParentToolkit.copy())
                                .workspace(workspace)
                                .asLeafSubagent()
                                .maxIters(capturedMaxIters)
                                .environmentMemory(capturedEnvMemory)
                                .useLegacyXmlWorkspaceContext(capturedUseLegacyXmlWorkspaceContext)
                                .enableAgentTracingLog(capturedAgentTracingLogEnabled)
                                .maxContextTokens(capturedMaxContextTokens);

                capturedAdditionalContextFiles.forEach(sub::additionalContextFile);

                if (capturedDisableFilesystemTools) sub.disableFilesystemTools();
                if (capturedDisableShellTool) sub.disableShellTool();
                if (capturedDisableMemoryTools) sub.disableMemoryTools();
                if (capturedDisableMemoryHooks) sub.disableMemoryHooks();
                if (capturedDisableSessionPersistence) sub.disableSessionPersistence();
                if (capturedDisableWorkspaceContext) sub.disableWorkspaceContext();

                if (!capturedSkillRepos.isEmpty()) sub.skillRepositories(capturedSkillRepos);
                if (capturedProjectGlobalSkillsDir != null) {
                    sub.projectGlobalSkillsDir(capturedProjectGlobalSkillsDir);
                }
                if (capturedBackend != null) sub.abstractFilesystem(capturedBackend);
                if (capturedModelExec != null) sub.modelExecutionConfig(capturedModelExec);
                if (capturedToolExec != null) sub.toolExecutionConfig(capturedToolExec);
                if (capturedGenOpts != null) sub.generateOptions(capturedGenOpts);
                if (capturedCompactionConfig != null) sub.compaction(capturedCompactionConfig);
                if (capturedToolResultEvictionConfig != null)
                    sub.toolResultEviction(capturedToolResultEvictionConfig);

                sub.hooks(capturedHooks);

                return sub.build();
            };
        }

        /**
         * Builds a factory for a user-declared subagent from a {@link SubagentDeclaration}.
         */
        /**
         * 从 {@link SubagentDeclaration} 为用户声明的子代理构建工厂。
         */
        private SubagentFactory buildDeclaredFactory(
                SubagentDeclaration decl, Path mainWorkspace, SandboxBackedFilesystem sandboxFs) {
            final Model capturedModel = this.model;
            final Toolkit capturedParentToolkit =
                    this.toolkit != null ? this.toolkit.copy() : new Toolkit();
            final Function<String, Model> capturedResolver = this.modelResolver;
            final AbstractFilesystem capturedSharedBackend =
                    sandboxFs != null ? sandboxFs : this.abstractFilesystem;
            final boolean capturedUseLegacyXmlWorkspaceContext = this.useLegacyXmlWorkspaceContext;
            final boolean capturedDisableFilesystemTools = this.disableFilesystemTools;
            final boolean capturedDisableShellTool = this.disableShellTool;
            final boolean capturedDisableMemoryTools = this.disableMemoryTools;
            final boolean capturedDisableMemoryHooks = this.disableMemoryHooks;
            final boolean capturedDisableSessionPersistence = this.disableSessionPersistence;

            return () -> {
                if (decl.isRemote()) {
                    return new RemoteSubagentStub(decl.getName(), decl.getDescription());
                }
                // ---- Resolve workspace root ----
                // ---- 解析工作区根目录 ----
                Path runtimeWorkspace = resolveDeclaredWorkspace(decl, mainWorkspace);

                // ---- Resolve system prompt ----
                // ---- 解析系统提示 ----
                String sysPromptBase = resolveDeclaredSysPromptBase(decl);

                // ---- Resolve model ----
                // ---- 解析模型 ----
                Model effectiveModel =
                        resolveModel(
                                decl.getModel(), capturedModel, capturedResolver, decl.getName());

                // ---- Build child agent ----
                // ---- 构建子代理 ----
                Builder sub =
                        HarnessAgent.builder()
                                .name(decl.getName())
                                .description(decl.getDescription())
                                .model(effectiveModel)
                                .toolkit(
                                        allowlistedInheritedToolkit(
                                                capturedParentToolkit, decl.getTools()))
                                .workspace(runtimeWorkspace)
                                .maxIters(decl.getMaxIters())
                                .asLeafSubagent()
                                .useLegacyXmlWorkspaceContext(capturedUseLegacyXmlWorkspaceContext)
                                .sysPrompt(buildSubagentSysPrompt(sysPromptBase));

                // Shared mode reuses the parent's filesystem backend
                // 共享模式重用父代理的文件系统后端
                if (decl.getWorkspaceMode() == WorkspaceMode.SHARED
                        && capturedSharedBackend != null) {
                    sub.abstractFilesystem(capturedSharedBackend);
                }

                if (capturedDisableFilesystemTools) sub.disableFilesystemTools();
                if (capturedDisableShellTool) sub.disableShellTool();
                if (capturedDisableMemoryTools) sub.disableMemoryTools();
                if (capturedDisableMemoryHooks) sub.disableMemoryHooks();
                if (capturedDisableSessionPersistence) sub.disableSessionPersistence();

                return sub.build();
            };
        }

        /**
         * Returns a defensive copy of inherited parent tools filtered by the optional allowlist.
         */
        /**
         * 返回由可选允许列表过滤后的继承父工具集的防御性副本。
         */
        private static Toolkit allowlistedInheritedToolkit(
                Toolkit parentToolkit, List<String> allowlist) {
            Toolkit toolkit = parentToolkit != null ? parentToolkit.copy() : new Toolkit();
            if (allowlist == null || allowlist.isEmpty()) {
                return toolkit;
            }
            List<String> toRemove =
                    toolkit.getToolSchemas().stream()
                            .map(ToolSchema::getName)
                            .filter(name -> !allowlist.contains(name))
                            .toList();
            toRemove.forEach(toolkit::removeTool);
            return toolkit;
        }

        /**
         * Resolves the runtime workspace root for a declared subagent according to the five-row
         * decision table. Creates the auto-generated isolated directory when needed.
         */
        /**
         * 根据五行决策表为声明的子代理解析运行时工作区根目录。
         * 在需要时创建自动生成的隔离目录。
         */
        private static Path resolveDeclaredWorkspace(SubagentDeclaration decl, Path mainWorkspace) {
            if (decl.getWorkspacePath() != null) {
                if (decl.getWorkspaceMode() == WorkspaceMode.SHARED) {
                    return mainWorkspace;
                }
                return decl.getWorkspacePath();
            }
            if (decl.getWorkspaceMode() == WorkspaceMode.SHARED) {
                return mainWorkspace;
            }
            // ISOLATED + no path → auto-create agents/<name>/workspace/
            // 隔离 + 无路径 → 自动创建 agents/<name>/workspace/
            Path isolated =
                    mainWorkspace.resolve("agents").resolve(decl.getName()).resolve("workspace");
            try {
                Files.createDirectories(isolated);
            } catch (Exception e) {
                log.warn(
                        "Failed to create isolated workspace for subagent '{}' at {}: {}",
                        decl.getName(),
                        isolated,
                        e.getMessage());
            }
            return isolated;
        }

        /**
         * Resolves the system-prompt <em>base</em> for a declared subagent.
         */
        /**
         * 为声明的子代理解析系统提示<em>基础</em>。
         */
        private static String resolveDeclaredSysPromptBase(SubagentDeclaration decl) {
            if (decl.getWorkspacePath() != null) {
                Path agentsMd = decl.getWorkspacePath().resolve("AGENTS.md");
                if (Files.isRegularFile(agentsMd)) {
                    try {
                        return Files.readString(agentsMd, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        log.warn(
                                "Failed to read AGENTS.md for subagent '{}' from {}: {}",
                                decl.getName(),
                                agentsMd,
                                e.getMessage());
                    }
                }
                return "";
            }
            String inline = decl.getInlineAgentsBody();
            return (inline != null) ? inline : "";
        }

        /**
         * Resolves the effective {@link Model} for a subagent, applying the optional per-subagent
         * model override.
         */
        /**
         * 为子代理解析有效的 {@link Model}，应用可选的按子代理模型覆盖。
         */
        private static Model resolveModel(
                String modelOverride,
                Model parentModel,
                Function<String, Model> resolver,
                String subagentName) {
            if (modelOverride == null || modelOverride.isBlank()) {
                return parentModel;
            }
            Function<String, Model> effectiveResolver =
                    resolver != null ? resolver : ModelRegistry::resolve;
            if (ModelRegistry.canResolve(modelOverride) || resolver != null) {
                try {
                    Model resolved = effectiveResolver.apply(modelOverride);
                    if (resolved != null) {
                        log.debug(
                                "Subagent '{}' using overridden model: {}",
                                subagentName,
                                modelOverride);
                        return resolved;
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to resolve model '{}' for subagent '{}', falling back to"
                                    + " parent model: {}",
                            modelOverride,
                            subagentName,
                            e.getMessage());
                }
            }
            return parentModel;
        }

        // -----------------------------------------------------------------
        //  Skills
        // -----------------------------------------------------------------

        /**
         * Assembles the ordered list of skill repositories used by this build (low → high
         * priority). Returns an empty list when no source resolves.
         */
        /**
         * 组合此构建使用的优先级排序的技能存储库列表（低 → 高优先级）。
         * 当没有来源可解析时返回空列表。
         */
        private List<AgentSkillRepository> composeSkillRepositories(
                WorkspaceManager wsManager,
                AbstractFilesystem filesystem,
                Supplier<RuntimeContext> currentRcSupplier) {
            List<AgentSkillRepository> ordered = new ArrayList<>();

            // Layer 1 (lowest priority): project-global skills directory.
            // 第 1 层（最低优先级）：项目全局技能目录。
            if (projectGlobalSkillsDir != null && Files.isDirectory(projectGlobalSkillsDir)) {
                try {
                    ordered.add(new FileSystemSkillRepository(projectGlobalSkillsDir));
                } catch (Exception e) {
                    log.warn(
                            "Failed to register project-global skills dir {}: {}",
                            projectGlobalSkillsDir,
                            e.getMessage());
                }
            }

            // Layer 2: marketplace repositories (user-supplied).
            // 第 2 层：市场存储库（用户提供）。
            ordered.addAll(skillRepositories);

            // Layer 3: workspace agent-shared directory.
            // 第 3 层：工作区代理共享目录。
            Path workspaceSkillsDir = wsManager.getSkillsDir();
            if (workspaceSkillsDir != null && Files.isDirectory(workspaceSkillsDir)) {
                try {
                    ordered.add(new FileSystemSkillRepository(workspaceSkillsDir));
                } catch (Exception e) {
                    log.warn(
                            "Failed to load workspace skills from {}: {}",
                            workspaceSkillsDir,
                            e.getMessage());
                }
            }

            // Layer 4 (highest priority): per-user namespaced filesystem view.
            // 第 4 层（最高优先级）：按用户命名空间文件系统视图。
            if (filesystem != null) {
                ordered.add(
                        new FilesystemBackedSkillRepository(
                                filesystem, "skills", currentRcSupplier, "workspace-namespaced"));
            }

            return ordered;
        }

        /**
         * Eagerly assembles a static {@link SkillBox} from {@code repos} (low → high priority)
         * so callers using {@link #disableDynamicSkills()} keep the legacy {@code SkillHook}
         * path while still benefiting from the additive composition.
         */
        /**
         * 从 {@code repos}（低 → 高优先级）急切地组装静态 {@link SkillBox}，
         * 以便使用 {@link #disableDynamicSkills()} 的调用方保持旧版 {@code SkillHook}
         * 路径，同时仍然受益于累加组合。
         */
        private static SkillBox staticSkillBoxFromRepos(
                List<AgentSkillRepository> repos, Toolkit agentToolkit) {
            LinkedHashMap<String, AgentSkill> merged = new LinkedHashMap<>();
            for (AgentSkillRepository repo : repos) {
                try {
                    List<AgentSkill> skills = repo.getAllSkills();
                    if (skills == null) {
                        continue;
                    }
                    for (AgentSkill skill : skills) {
                        if (skill != null && skill.getName() != null) {
                            merged.put(skill.getName(), skill);
                        }
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to load skills from {}: {}",
                            repo.getClass().getSimpleName(),
                            e.getMessage());
                }
            }
            if (merged.isEmpty()) {
                return null;
            }
            SkillBox box = new SkillBox(agentToolkit);
            for (AgentSkill skill : merged.values()) {
                box.registerSkill(skill);
            }
            log.info("Loaded {} skills from {} repositories (static)", merged.size(), repos.size());
            return box;
        }

        private record SubagentFactoryEntry(String name, Function<String, Agent> factory) {}

        /** Marks this build as a leaf subagent (no nested subagent orchestration). */
        /** 将此构建标记为叶子子代理（无嵌套子代理编排）。 */
        private Builder asLeafSubagent() {
            this.leafSubagent = true;
            return this;
        }

        /**
         * Builds the full subagent entry list including the local-disk scan of the
         * {@code subagents/} directory.
         */
        /**
         * 构建完整的子代理条目列表，包括对 {@code subagents/} 目录的本地磁盘扫描。
         */
        private List<SubagentEntry> buildSubagentEntries(
                Path resolvedWorkspace, SandboxBackedFilesystem sandboxFs) {
            List<SubagentEntry> entries = new ArrayList<>(buildStaticSubagentEntries(
                    resolvedWorkspace, sandboxFs));

            // Scan local-disk subagents/ directory for additional declarations
            // 扫描本地磁盘 subagents/ 目录以查找额外的声明
            Path subagentsDir = resolvedWorkspace.resolve("subagents");
            if (Files.isDirectory(subagentsDir)) {
                try (var files = Files.list(subagentsDir)) {
                    files.filter(f -> f.toString().endsWith(".md"))
                            .forEach(f -> {
                                try {
                                    String content = Files.readString(f);
                                    AgentSpecLoader.loadFromMarkdown(content)
                                            .forEach(decl -> {
                                                entries.add(new SubagentEntry(
                                                        decl.getName(),
                                                        decl.getDescription(),
                                                        buildDeclaredFactory(
                                                                decl, resolvedWorkspace, sandboxFs),
                                                        decl));
                                            });
                                } catch (Exception e) {
                                    log.warn("Failed to load subagent from {}: {}",
                                            f, e.getMessage());
                                }
                            });
                } catch (Exception e) {
                    log.warn("Failed to list subagents directory: {}", e.getMessage());
                }
            }

            return entries;
        }

        private <T> List<T> concat(List<T> a, List<T> b) {
            List<T> result = new ArrayList<>(a);
            result.addAll(b);
            return result;
        }
    }
}
