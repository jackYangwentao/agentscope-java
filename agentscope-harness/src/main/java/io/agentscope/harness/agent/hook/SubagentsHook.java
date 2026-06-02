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
package io.agentscope.harness.agent.hook;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.hook.RuntimeContextAware;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.subagent.AgentSpecLoader;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.SubagentFactory;
import io.agentscope.harness.agent.subagent.task.BackgroundTask;
import io.agentscope.harness.agent.subagent.task.DefaultTaskRepository;
import io.agentscope.harness.agent.subagent.task.TaskRepository;
import io.agentscope.harness.agent.tool.AgentSpawnTool;
import io.agentscope.harness.agent.tool.TaskTool;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Hook that provides the managed subagent mechanism.
 *
 * <p>In <strong>default mode</strong> (standalone {@code HarnessAgent}), this hook creates an
 * {@link AgentSpawnTool} backed by a {@link DefaultAgentManager}. In <strong>session mode</strong>
 * (orchestrated via {@code AgentBootstrap}), an external tool (typically {@code SessionsTool}) is
 * injected, replacing the default {@link AgentSpawnTool}.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li>Registers the subagent tool and {@link TaskTool} as agent tools
 *   <li>On {@link PreCallEvent}, reloads subagent declarations from the workspace filesystem
 *       (namespace-scoped) to support per-user subagent isolation
 *   <li>Injects rich subagent usage guidance into the unified system message at
 *       {@link PreReasoningEvent} time via {@link PreReasoningEvent#appendSystemContent}.
 *       Because each {@link PreReasoningEvent} starts from a fresh copy of the frozen base
 *       system message, calling {@code appendSystemContent} on every iteration is safe —
 *       content never accumulates across iterations.
 *   <li>Appends a concise summary of current async tasks to the system content each turn
 *       (at most 10 tasks), so the model always has current task state even after compaction.
 * </ol>
 *
 * <p>托管子 agent 机制钩子。
 * 在<strong>默认模式</strong>（独立的 HarnessAgent）下，创建由 DefaultAgentManager 支持的 AgentSpawnTool。
 * 在<strong>会话模式</strong>（通过 AgentBootstrap 编排）下，注入外部工具（通常是 SessionsTool）。
 * 职责包括：注册子 agent 工具和 TaskTool、从工作区文件系统重新加载子 agent 声明、
 * 在 PreReasoningEvent 时将子 agent 使用指南注入统一的系统消息、每轮附加简要的异步任务摘要。
 */
public class SubagentsHook implements Hook, RuntimeContextAware {

    private static final Logger log = LoggerFactory.getLogger(SubagentsHook.class);

    /** Hook priority used by both this hook and {@link DynamicSubagentsHook}. */
    /** 此钩子和 {@link DynamicSubagentsHook} 使用的钩子优先级。 */
    public static final int SUBAGENT_HOOK_PRIORITY = 80;

    private static final DateTimeFormatter ISO_SHORT =
            DateTimeFormatter.ofPattern("HH:mm'Z'").withZone(ZoneOffset.UTC);

    private static final int MAX_TASK_SUMMARY_ENTRIES = 10;

    // @formatter:off
    private static final String SUBAGENT_SECTION_TEMPLATE =
            """

            ## Subagents

            You have access to subagent tools for spawning and coordinating isolated subagents.
            Subagents are ephemeral — they live only for the duration of the task and return a single result.

            ### Agent Tools

            **`%s`** — Spawn an isolated subagent
            - `agent_id` (required): which subagent to instantiate
            - `task` (optional): initial prompt; omit to create a persistent session
            - `label` (optional): human-readable name for referencing via send
            - `timeout_seconds`: wait time; 0=fire-and-forget (returns task_id), default=30, max=600
            - Response always includes `agent_key:` (opaque handle) — save it for follow-up sends

            **`%s`** — Send a follow-up message to an existing subagent
            - `agent_key`: copy the **full value** after `agent_key:` from spawn output (starts with `agent:`). This is NOT `agent_id`, NOT `session_id`, and NOT `task_id`
            - Or use `label` if you set one at spawn (mutually exclusive with agent_key)
            - `message` (required): content to send
            - `timeout_seconds`: 0=fire-and-forget, >0=wait for reply (default: 30)

            **`%s`** — List active subagents

            ### Task Tools (for async/background operations)

            **`task_output`** — Retrieve the result of a background task by task_id.
            - Prefer `block=false` to check status without blocking.
            - Only use `block=true` (default) when ready to wait for the result.
            - **Do NOT call immediately after launching** — the task has just started and will not be ready yet.

            **`task_cancel`** — Cancel a running background task by task_id. No effect on already-completed tasks.

            **`task_list`** — List all background tasks with their current, live statuses (reads from durable storage).
            - Always accurate even after conversation compaction or node migration.
            - Use after compaction or session resume to recover all task IDs and current state.

            ### CRITICAL async task rules
            1. **Never poll immediately** after launching a task. Return control to the user instead.
            2. **Never poll in a loop** — task_output does not short-circuit; every call blocks or waits.
            3. **Task status in conversation history is STALE** — do not report it. Always call `task_output(block=false)` or `task_list()` for the current state.
            4. After compaction or session resume, call `task_list()` first to recover all task IDs and statuses.
            5. For a single task status check, use `task_output(task_id=..., block=false)`.

            ### Available agent ids
            %s

            ### When to use subagents
            - When a task is complex and multi-step, and can be fully delegated in isolation
            - When a task is independent of other tasks and can run in parallel
            - When a task requires focused reasoning or heavy context usage that would bloat the main thread
            - When sandboxing improves reliability (e.g. code analysis, structured searches, data formatting)
            - When you only care about the output, not the intermediate steps (e.g. research → synthesized report)

            ### When NOT to use subagents
            - If the task is trivial (a few tool calls or simple lookup)
            - If you need to see intermediate reasoning or steps after completion
            - If delegating does not reduce token usage, complexity, or context switching
            - If splitting would add latency without benefit

            ### Subagent lifecycle
            1. **Spawn** → Provide clear role, instructions, and expected output format
            2. **Run** → The subagent completes the task autonomously
            3. **Return** → The subagent provides a single structured result
            4. **Reconcile** → Incorporate or synthesize the result into the main thread

            ### Usage patterns
            - **Parallel execution**: Launch multiple subagents concurrently with timeout_seconds=0 when tasks are independent, then collect results with task_output(block=false) after a delay
            - **Sync delegation**: Use default timeout for simple one-shot delegation
            - **Persistent session**: Spawn without a task, then use send for multi-turn interaction
            - **Cancel stale work**: Use task_cancel to stop background tasks that are no longer needed
            - Subagent results are NOT visible to the user — always summarize them in your response
            """;
    // @formatter:on

    private final List<SubagentEntry> baseEntries;
    private volatile List<SubagentEntry> entries;
    private final Object subagentTool;
    private final TaskTool taskTool;
    private final TaskRepository taskRepository;
    private final boolean isSessionMode;
    private volatile RuntimeContext runtimeContext;

    private final AbstractFilesystem filesystem;
    private final Path mainWorkspace;
    private final Function<SubagentDeclaration, SubagentFactory> factoryBuilder;
    private final DefaultAgentManager agentManager;

    /**
     * Default mode: creates {@link AgentSpawnTool} + {@link DefaultAgentManager} internally.
     *
     * <p>The user-id is derived from each tool invocation's {@link RuntimeContext} rather than a
     * shared supplier — this avoids identity races when a single agent serves concurrent callers.
     *
     * <p>默认模式：在内部创建 {@link AgentSpawnTool} + {@link DefaultAgentManager}。
     * 用户 ID 源自每次工具调用的 {@link RuntimeContext} 而不是共享的提供者 —
     * 这避免了当单个 agent 服务并发调用者时的身份竞争。
     *
     * @param entries subagent descriptors (agent_id, description, factory)
     *     <p>子 agent 描述符（agent_id、描述、工厂）
     * @param taskRepository background task store for async operations
     *     <p>用于异步操作的后台任务存储
     * @param workspaceManager workspace accessor for session file path resolution
     *     <p>用于会话文件路径解析的工作区访问器
     * @param filesystem the filesystem layer for dynamic subagent discovery (may be {@code null})
     *     <p>用于动态子 agent 发现的文件系统层（可以为 {@code null}）
     * @param mainWorkspace the parent workspace path for resolving subagent workspace paths
     *     <p>用于解析子 agent 工作区路径的父工作区路径
     * @param factoryBuilder creates a {@link SubagentFactory} from a {@link SubagentDeclaration};
     *     may be {@code null} if dynamic discovery is not needed
     *     <p>从 {@link SubagentDeclaration} 创建 {@link SubagentFactory}；
     *     如果不需要动态发现则可以为 {@code null}
     */
    public SubagentsHook(
            List<SubagentEntry> entries,
            TaskRepository taskRepository,
            WorkspaceManager workspaceManager,
            AbstractFilesystem filesystem,
            Path mainWorkspace,
            Function<SubagentDeclaration, SubagentFactory> factoryBuilder) {
        this.baseEntries = List.copyOf(entries);
        this.entries = this.baseEntries;
        this.isSessionMode = false;
        DefaultAgentManager dam = new DefaultAgentManager(entries, workspaceManager);
        this.agentManager = dam;
        TaskRepository repo = taskRepository != null ? taskRepository : new DefaultTaskRepository();
        this.taskRepository = repo;
        this.subagentTool = new AgentSpawnTool(dam, repo, 0);
        this.taskTool = new TaskTool(repo);
        this.filesystem = filesystem;
        this.mainWorkspace = mainWorkspace;
        this.factoryBuilder = factoryBuilder;
    }

    /**
     * Default mode without dynamic reload support. Equivalent to passing {@code null} for
     * filesystem, mainWorkspace, and factoryBuilder.
     *
     * <p>不带动态重新加载支持的默认模式。相当于为 filesystem、mainWorkspace 和 factoryBuilder 传递 {@code null}。
     */
    public SubagentsHook(
            List<SubagentEntry> entries,
            TaskRepository taskRepository,
            WorkspaceManager workspaceManager) {
        this(entries, taskRepository, workspaceManager, null, null, null);
    }

    /**
     * Session mode: uses the externally provided tool (typically {@code SessionsTool}).
     *
     * <p>会话模式：使用外部提供的工具（通常是 {@code SessionsTool}）。
     *
     * @param entries subagent descriptors (for prompt injection — agent id listing)
     *     <p>子 agent 描述符（用于提示注入 — agent ID 列表）
     * @param externalSubagentTool the external tool instance (e.g. SessionsTool)
     *     <p>外部工具实例（例如 SessionsTool）
     * @param taskRepository background task store for async operations
     *     <p>用于异步操作的后台任务存储
     */
    public SubagentsHook(
            List<SubagentEntry> entries,
            Object externalSubagentTool,
            TaskRepository taskRepository) {
        this.baseEntries = List.copyOf(entries);
        this.entries = this.baseEntries;
        this.isSessionMode = true;
        this.agentManager = null;
        this.subagentTool = externalSubagentTool;
        TaskRepository repo = taskRepository != null ? taskRepository : new DefaultTaskRepository();
        this.taskRepository = repo;
        this.taskTool = new TaskTool(repo);
        this.filesystem = null;
        this.mainWorkspace = null;
        this.factoryBuilder = null;
    }

    public SubagentsHook(List<SubagentEntry> entries) {
        this(entries, (TaskRepository) null, (WorkspaceManager) null);
    }

    @Override
    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    @Override
    public List<Object> tools() {
        if (entries.isEmpty()) {
            return List.of();
        }
        return List.of(subagentTool, taskTool);
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreCallEvent) {
            reloadSubagentEntries();
        }
        if (event instanceof PreReasoningEvent preReasoning) {
            injectSubagentPrompt(preReasoning);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return SUBAGENT_HOOK_PRIORITY;
    }

    /**
     * Reloads subagent entries from the filesystem, merging with base entries.
     * Filesystem-discovered entries that do not share a name with an existing base entry
     * are appended.
     *
     * <p>从文件系统重新加载子 agent 条目，与基础条目合并。
     * 文件系统发现的与现有基础条目不共享名称的条目会被附加。
     */
    private void reloadSubagentEntries() {
        if (filesystem == null || factoryBuilder == null || isSessionMode) {
            return;
        }
        try {
            List<SubagentDeclaration> decls =
                    AgentSpecLoader.loadFromFilesystem(filesystem, mainWorkspace);

            List<SubagentEntry> newEntries = new ArrayList<>(baseEntries);
            for (SubagentDeclaration decl : decls) {
                boolean alreadyExists =
                        baseEntries.stream().anyMatch(e -> e.name().equals(decl.getName()));
                if (!alreadyExists) {
                    newEntries.add(
                            new SubagentEntry(
                                    decl.getName(),
                                    decl.getDescription(),
                                    factoryBuilder.apply(decl),
                                    decl));
                }
            }

            this.entries = List.copyOf(newEntries);
            if (agentManager != null) {
                agentManager.refreshEntries(this.entries);
            }
        } catch (Exception e) {
            log.warn("Failed to reload subagent entries from filesystem: {}", e.getMessage());
        }
    }

    /**
     * Injects the subagent prompt section and task summary into the system content of
     * a {@link PreReasoningEvent}.
     *
     * <p>将子 agent 提示部分和任务摘要注入 {@link PreReasoningEvent} 的系统内容。
     */
    private void injectSubagentPrompt(PreReasoningEvent event) {
        List<SubagentEntry> currentEntries = this.entries;
        if (currentEntries.isEmpty()) {
            return;
        }
        event.appendSystemContent(renderSubagentSection(currentEntries, isSessionMode));

        String taskSummary = buildTaskSummary();
        if (taskSummary != null) {
            event.appendSystemContent(taskSummary);
        }
    }

    /**
     * Renders the {@code ## Subagents} system-prompt section for the supplied entries. Shared by
     * {@link SubagentsHook} and {@link DynamicSubagentsHook}.
     *
     * <p>为提供的条目渲染 {@code ## Subagents} 系统提示部分。
     * 由 {@link SubagentsHook} 和 {@link DynamicSubagentsHook} 共享。
     */
    public static String renderSubagentSection(List<SubagentEntry> entries, boolean isSessionMode) {
        String agentList =
                entries.stream()
                        .map(e -> String.format("- `%s`: %s", e.name(), e.description()))
                        .collect(Collectors.joining("\n"));
        String spawnName = isSessionMode ? "sessions_spawn" : "agent_spawn";
        String sendName = isSessionMode ? "sessions_send" : "agent_send";
        String listName = isSessionMode ? "sessions_list" : "agent_list";
        return String.format(SUBAGENT_SECTION_TEMPLATE, spawnName, sendName, listName, agentList);
    }

    /**
     * Builds a concise task summary string for the current session, or {@code null} if there are
     * no tasks to report. The summary is injected into the system content every turn so the model
     * always has current task IDs and statuses — even after conversation compaction.
     *
     * <p>为当前会话构建简洁的任务摘要字符串，如果没有要报告的任务则返回 {@code null}。
     * 摘要每轮都会注入系统内容，以便模型始终拥有当前的任务 ID 和状态 — 即使在对话压缩之后。
     */
    private String buildTaskSummary() {
        return buildTaskSummary(this.taskRepository, this.runtimeContext);
    }

    /**
     * Static variant of {@link #buildTaskSummary()} for use by {@link DynamicSubagentsHook}.
     * Returns {@code null} when no tasks should be rendered.
     *
     * <p>{@link #buildTaskSummary()} 的静态变体，供 {@link DynamicSubagentsHook} 使用。
     * 当没有任务需要渲染时返回 {@code null}。
     */
    public static String buildTaskSummary(TaskRepository repo, RuntimeContext ctx) {
        if (repo == null) {
            return null;
        }
        String sessionId = ctx != null ? ctx.getSessionId() : null;
        Collection<BackgroundTask> tasks = repo.listTasks(ctx, sessionId, null);
        if (tasks.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("\n### Async tasks (current session)\n");
        int count = 0;
        for (BackgroundTask task : tasks) {
            if (count >= MAX_TASK_SUMMARY_ENTRIES) {
                sb.append("- ... (")
                        .append(tasks.size() - MAX_TASK_SUMMARY_ENTRIES)
                        .append(" more — use task_list() to see all)\n");
                break;
            }
            sb.append("- task_id: ").append(task.getTaskId());
            if (task.getAgentId() != null) {
                sb.append("  agent: ").append(task.getAgentId());
            }
            sb.append("  status: ").append(task.getTaskStatus().name().toLowerCase());
            sb.append("  started: ").append(ISO_SHORT.format(task.getCreatedAt()));
            sb.append('\n');
            count++;
        }
        sb.append(
                "(Status above reflects current state; use task_output or task_list for"
                        + " latest.)\n");
        return sb.toString();
    }

    /**
     * Descriptor for a subagent identified by agent id, with its description, {@link
     * SubagentFactory}, and optional {@link io.agentscope.harness.agent.subagent.SubagentDeclaration}
     * (for remote URL and headers).
     *
     * <p>子 agent 描述符，由 agent ID 标识，包含其描述、{@link SubagentFactory} 和可选的
     * {@link io.agentscope.harness.agent.subagent.SubagentDeclaration}（用于远程 URL 和请求头）。
     */
    public record SubagentEntry(
            String name,
            String description,
            SubagentFactory factory,
            SubagentDeclaration declaration) {
        public SubagentEntry(String name, String description, SubagentFactory factory) {
            this(name, description, factory, null);
        }
    }
}
