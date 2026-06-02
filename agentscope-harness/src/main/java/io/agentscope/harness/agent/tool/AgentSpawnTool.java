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
package io.agentscope.harness.agent.tool;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.EventSource;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.SubagentEventBus;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.task.BackgroundTask;
import io.agentscope.harness.agent.subagent.task.TaskRepository;
import io.agentscope.harness.agent.subagent.task.TaskRunSpec;
import io.agentscope.harness.agent.subagent.task.TaskStatus;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 供代理内部使用的轻量子代理工具。相比 {@code SessionsTool} 更轻量：
 * Simple subagent tool for agent-internal use. Much lighter than {@code SessionsTool}:
 *
 * <ul>
 *   <li>{@code agent_spawn} — 生成子代理，运行任务，返回结果（同步或异步）
 *   <li>{@code agent_send} — 向先前生成的子代理发送后续消息
 *   <li>{@code agent_list} — 列出活跃的子代理
 * </ul>
 *
 * <p>无会话、无通道、无运行注册、无公告分发。仅"创建代理、调用、返回结果"。
 * No sessions, no lanes, no run registry, no announce dispatch. Just "create agent, invoke,
 * return result". Uses {@link DefaultAgentManager} for agent creation and invocation only.
 *
 * <p>异步任务（{@code timeout_seconds=0}）提交到以当前 {@link RuntimeContext} 会话 ID 为作用域的
 * {@link TaskRepository}。这使得任务状态在工作空间存储中可见，支持跨节点检索和压缩后恢复。
 * Async tasks ({@code timeout_seconds=0}) are submitted to the {@link TaskRepository} scoped
 * by the current session ID from {@link RuntimeContext}. This makes task state visible in
 * workspace storage for cross-node retrieval and recovery after compaction.
 *
 * <h2>流式处理 / Streaming</h2>
 *
 * <p>{@code agent_spawn} 和 {@code agent_send} 返回 {@link Mono}{@code <String>}，使得框架的
 * 响应式工具调用管道可以在父代理的流式处理链中订阅它们。
 * {@code agent_spawn} and {@code agent_send} return {@link Mono}{@code <String>} so that the
 * framework's reactive tool-invocation pipeline (see {@code ToolMethodInvoker}) can subscribe them
 * within the parent agent's streaming chain. When a {@link SubagentEventBus} is present in the
 * Reactor Context (injected by {@code AgentBase.createEventStream}), every child {@link
 * io.agentscope.core.agent.Event} is forwarded to the parent sink in real time, giving consumers
 * a flattened event stream across the full call hierarchy. When no bus is present (plain {@code
 * call()} mode), execution falls back to the non-streaming {@code invokeAgent} path with no
 * overhead.
 */
public class AgentSpawnTool {

    private static final Logger log = LoggerFactory.getLogger(AgentSpawnTool.class);

    // 默认超时时间：30秒
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    // 最大超时时间：600秒
    private static final int MAX_TIMEOUT_SECONDS = 600;
    private static final int MAX_SPAWN_DEPTH = 3;

    private static final String BG_RESULT_TEMPLATE =
            """
            status: accepted
            task_id: %s
            Use task_output(task_id='%s', block=false) to check status, \
            task_cancel(task_id='%s') to cancel, or task_list() to see all tasks. \
            Do NOT call task_output immediately — the task has just started.\
            """;

    private final DefaultAgentManager agentManager;
    private final TaskRepository taskRepository;
    private final int parentSpawnDepth;

    /**
     * 已生成的子代理记录，包含生成的关键信息。
     */
    private record SpawnedAgent(
            String key, String agentId, String sessionId, String label, Agent agent, int depth) {}

    private final ConcurrentHashMap<String, SpawnedAgent> agentsByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> labelToKey = new ConcurrentHashMap<>();

    /**
     * 创建 {@code AgentSpawnTool}，从每次工具调用的 {@link RuntimeContext} 派生活跃用户 ID，
     * 而非使用共享提供者——这防止了单个代理实例服务并发调用者时的身份竞态。
     * Creates an {@code AgentSpawnTool} that derives the active user-id from each tool call's
     * {@link RuntimeContext}, rather than a shared supplier — this prevents identity races when a
     * single agent instance serves concurrent callers.
     *
     * @param agentManager      子代理的工厂和调用器
     * @param taskRepository    后台任务存储
     * @param parentSpawnDepth 父级当前的生成深度（顶层主代理为 0）
     */
    public AgentSpawnTool(
            DefaultAgentManager agentManager, TaskRepository taskRepository, int parentSpawnDepth) {
        this.agentManager = Objects.requireNonNull(agentManager, "agentManager");
        this.taskRepository = taskRepository;
        this.parentSpawnDepth = parentSpawnDepth;
    }

    /**
     * 生成独立的子代理用于委派或后台工作。
     * 每个响应以三行开头：agent_key（原样传递给 agent_send）、agent_id（子代理类型名）、session_id（内部使用）。
     * 同步模式返回回复内容；异步模式（timeout_seconds=0）添加 task_id 用于 task_output。
     *
     * @Tool agent_spawn
     */
    @Tool(
            name = "agent_spawn",
            description =
                    """
                    Spawn an isolated subagent for delegated or background work. \
                    Every response starts with three lines: agent_key (pass this verbatim to \
                    agent_send as agent_key), agent_id (the subagent type name), and session_id \
                    (internal; do not use as agent_key). Sync mode returns the reply below that; \
                    async (timeout_seconds=0) adds task_id for task_output — task_id is NOT agent_key.\
                    """)
    public Mono<String> agentSpawn(
            RuntimeContext runtimeContext,
            @ToolParam(name = "agent_id", description = "Subagent identifier to instantiate")
                    String agentId,
            @ToolParam(
                            name = "task",
                            description = "Task or prompt to send to the spawned agent",
                            required = false)
                    String task,
            @ToolParam(
                            name = "label",
                            description =
                                    "Optional human-readable label for referencing via agent_send",
                            required = false)
                    String label,
            @ToolParam(
                            name = "timeout_seconds",
                            description =
                                    """
                                    Max seconds to wait for the task result. 0=fire-and-forget, \
                                    returns task_id. Default: 30. Max: 600.\
                                    """,
                            required = false)
                    Integer timeoutSeconds) {

        System.err.println(
                "[agentSpawn] called agentId="
                        + agentId
                        + " timeoutSeconds="
                        + timeoutSeconds
                        + " task="
                        + task);
        int nextDepth = parentSpawnDepth + 1;
        if (nextDepth > MAX_SPAWN_DEPTH) {
            System.err.println("[agentSpawn] depth exceeded");
            return Mono.just("Error: Maximum spawn depth exceeded (max=" + MAX_SPAWN_DEPTH + ")");
        }
        String canonLabel = label != null && !label.isBlank() ? label.trim() : null;
        if (canonLabel != null && labelToKey.containsKey(canonLabel.toLowerCase())) {
            return Mono.just("Error: Label already in use: " + canonLabel);
        }

        Optional<Agent> agentOpt = agentManager.createAgentIfPresent(agentId);
        if (agentOpt.isEmpty()) {
            System.err.println(
                    "[agentSpawn] unknown agentId=" + agentId + " known=" + agentManager);
            return Mono.just("Error: Unknown agent_id: " + agentId);
        }
        System.err.println("[agentSpawn] hasAgent=true, proceeding");
        Agent agent = agentOpt.get();
        String key = "agent:" + agentId + ":" + UUID.randomUUID();
        String sessionId = "sub-" + UUID.randomUUID();
        String currentUserId = runtimeContext != null ? runtimeContext.getUserId() : null;

        SpawnedAgent spawned =
                new SpawnedAgent(key, agentId, sessionId, canonLabel, agent, nextDepth);
        agentsByKey.put(key, spawned);
        if (canonLabel != null) {
            labelToKey.put(canonLabel.toLowerCase(), key);
        }

        String spawnInfo = formatSpawnInfo(key, agentId, sessionId);
        boolean hasTask = task != null && !task.isBlank();

        if (!hasTask) {
            return Mono.just(spawnInfo + "\nstatus: accepted");
        }

        long timeoutMs = resolveTimeoutMs(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
        String parentSessionId = runtimeContext != null ? runtimeContext.getSessionId() : null;
        var declOpt = agentManager.getDeclaration(agentId);
        boolean remote = declOpt.map(SubagentDeclaration::isRemote).orElse(false);

        if (timeoutMs == 0) {
            String taskId = "task_" + UUID.randomUUID();
            final String capturedTask = task;
            TaskRunSpec spec;
            if (remote) {
                SubagentDeclaration d = declOpt.get();
                spec =
                        new TaskRunSpec.RemoteTaskRunSpec(
                                d.getUrl(), d.getHeaders(), agentId, capturedTask);
            } else {
                spec =
                        new TaskRunSpec.LocalTaskRunSpec(
                                () -> {
                                    try {
                                        Msg reply =
                                                agentManager
                                                        .invokeAgent(
                                                                agent,
                                                                sessionId,
                                                                currentUserId,
                                                                capturedTask)
                                                        .block();
                                        return reply != null ? reply.getTextContent() : "";
                                    } catch (RuntimeException e) {
                                        return "Error: "
                                                + (e.getMessage() != null
                                                        ? e.getMessage()
                                                        : e.getClass().getSimpleName());
                                    }
                                });
            }
            taskRepository.putTask(runtimeContext, taskId, agentId, parentSessionId, spec);
            return Mono.just(
                    spawnInfo + "\n" + String.format(BG_RESULT_TEMPLATE, taskId, taskId, taskId));
        }

        if (remote) {
            final String finalTask = task;
            return Mono.fromCallable(
                    () ->
                            runRemoteSync(
                                    runtimeContext,
                                    spawnInfo,
                                    agentId,
                                    parentSessionId,
                                    declOpt.get(),
                                    finalTask.trim(),
                                    timeoutMs));
        }

        // Sync-local execution. Returns Mono<String> so that ToolMethodInvoker's flatMap
        // propagates the Reactor Context into the deferContextual below.
        final String finalTask = task.trim();
        final String finalSpawnInfo = spawnInfo;
        return execLocalSync(agent, sessionId, currentUserId, finalTask, spawned, runtimeContext)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(
                        reply -> {
                            String text = reply != null ? reply.getTextContent() : "";
                            return finalSpawnInfo + "\nstatus: ok\nreply:\n" + text;
                        })
                .onErrorResume(
                        e -> {
                            String err =
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : e.getClass().getSimpleName();
                            log.warn("agent_spawn execute failed: agentId={}", agentId, e);
                            return Mono.just(finalSpawnInfo + "\nstatus: error\nerror: " + err);
                        });
    }

    @Tool(
            name = "agent_send",
            description =
                    """
                    Send a message to an existing subagent. Use the exact string from the \
                    agent_key line of agent_spawn output (starts with agent:), or the label \
                    you set at spawn. Do not pass agent_id, session_id, or task_id here. \
                    timeout_seconds=0 returns task_id for task_output.\
                    """)
    public Mono<String> agentSend(
            RuntimeContext runtimeContext,
            @ToolParam(
                            name = "agent_key",
                            description =
                                    "Exact value from agent_spawn's first line after 'agent_key: '"
                                        + " (format agent:<type>:<uuid>). Not agent_id, session_id,"
                                        + " or task_id. Mutually exclusive with label.",
                            required = false)
                    String agentKey,
            @ToolParam(
                            name = "label",
                            description =
                                    "Agent label assigned at spawn time. Mutually exclusive with"
                                            + " agent_key.",
                            required = false)
                    String label,
            @ToolParam(name = "message", description = "Message to send to the subagent")
                    String message,
            @ToolParam(
                            name = "timeout_seconds",
                            description =
                                    """
                                    Max seconds to wait for a reply. 0=fire-and-forget, returns \
                                    task_id. Default: 30. Max: 600.\
                                    """,
                            required = false)
                    Integer timeoutSeconds) {

        boolean hasKey = agentKey != null && !agentKey.isBlank();
        boolean hasLabel = label != null && !label.isBlank();
        if (hasKey && hasLabel) {
            return Mono.just("Error: Provide either agent_key or label, not both.");
        }
        if (!hasKey && !hasLabel) {
            return Mono.just("Error: Either agent_key or label is required.");
        }
        if (message == null || message.isBlank()) {
            return Mono.just("Error: message is required");
        }

        String key;
        if (hasKey) {
            key = agentKey.trim();
        } else {
            key = labelToKey.get(label.trim().toLowerCase());
            if (key == null) {
                return Mono.just("Error: Unknown label: " + label.trim());
            }
        }

        SpawnedAgent spawned = agentsByKey.get(key);
        if (spawned == null) {
            return Mono.just("Error: Unknown agent_key: " + key);
        }

        long timeoutMs = resolveTimeoutMs(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS);
        String currentUserId = runtimeContext != null ? runtimeContext.getUserId() : null;
        String parentSessionId = runtimeContext != null ? runtimeContext.getSessionId() : null;
        var declOpt = agentManager.getDeclaration(spawned.agentId());
        boolean remote = declOpt.map(SubagentDeclaration::isRemote).orElse(false);

        if (timeoutMs == 0) {
            String taskId = "task_" + UUID.randomUUID();
            final String capturedMessage = message;
            TaskRunSpec spec;
            if (remote) {
                SubagentDeclaration d = declOpt.get();
                spec =
                        new TaskRunSpec.RemoteTaskRunSpec(
                                d.getUrl(), d.getHeaders(), spawned.agentId(), capturedMessage);
            } else {
                spec =
                        new TaskRunSpec.LocalTaskRunSpec(
                                () -> {
                                    try {
                                        Msg reply =
                                                agentManager
                                                        .invokeAgent(
                                                                spawned.agent(),
                                                                spawned.sessionId(),
                                                                currentUserId,
                                                                capturedMessage)
                                                        .block();
                                        return reply != null ? reply.getTextContent() : "";
                                    } catch (RuntimeException e) {
                                        return "Error: "
                                                + (e.getMessage() != null
                                                        ? e.getMessage()
                                                        : e.getClass().getSimpleName());
                                    }
                                });
            }
            taskRepository.putTask(
                    runtimeContext, taskId, spawned.agentId(), parentSessionId, spec);
            return Mono.just(String.format(BG_RESULT_TEMPLATE, taskId, taskId, taskId));
        }

        if (remote) {
            final String finalMessage = message;
            final String finalKey = key;
            return Mono.fromCallable(
                    () ->
                            runRemoteSync(
                                    runtimeContext,
                                    "agent_key: " + finalKey,
                                    spawned.agentId(),
                                    parentSessionId,
                                    declOpt.get(),
                                    finalMessage.trim(),
                                    timeoutMs));
        }

        final String finalKey = key;
        return execLocalSync(
                        spawned.agent(),
                        spawned.sessionId(),
                        currentUserId,
                        message.trim(),
                        spawned,
                        runtimeContext)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(
                        reply -> {
                            String text = reply != null ? reply.getTextContent() : "";
                            return "agent_key: " + finalKey + "\nstatus: ok\nreply:\n" + text;
                        })
                .onErrorResume(
                        e -> {
                            String err =
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : e.getClass().getSimpleName();
                            log.warn("agent_send failed: key={}", finalKey, e);
                            return Mono.just("Error: " + err);
                        });
    }

    /**
     * 列出此代理生成的所有活跃子代理。
     *
     * @Tool agent_list
     */
    @Tool(name = "agent_list", description = "List active subagents spawned by this agent.")
    public String agentList() {
        if (agentsByKey.isEmpty()) {
            return "No active subagents.";
        }

        StringBuilder sb =
                new StringBuilder("Active subagents (").append(agentsByKey.size()).append("):\n");
        for (SpawnedAgent a : agentsByKey.values()) {
            sb.append("- agent_key: ").append(a.key()).append("\n");
            sb.append("  agent_id: ").append(a.agentId()).append("\n");
            if (a.label() != null) {
                sb.append("  label: ").append(a.label()).append("\n");
            }
            sb.append("  spawn_depth: ").append(a.depth()).append("\n");
        }
        return sb.toString().trim();
    }

    // -----------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------

    /**
     * 返回调用本地子代理的 {@link Mono}。
     * Returns a {@link Mono} that invokes the local subagent.
     *
     * <p>当父代理处于 {@code stream()} 模式时，Reactor Context（通过 {@code AgentBase.createEventStream} 注入）
     * 中存在 {@link SubagentEventBus}。此时每个子事件会通过总线实时转发给父接收器，为上游消费者提供
     * 跨完整调用层级扁平化、有序的事件流。
     * When the parent is in {@code stream()} mode, a {@link SubagentEventBus} is present in
     * the Reactor Context (propagated by {@code AgentBase.createEventStream}). In that case every
     * child event is forwarded to the parent sink in real time via the bus, giving the upstream
     * consumer a flat, ordered event stream across the full call hierarchy.
     *
     * <p>当没有总线时（普通 {@code call()} 路径），回退到非流式的 {@code invokeAgent} 调用，无额外开销。
     * When no bus is present (plain {@code call()} path), execution falls back to the
     * non-streaming {@code invokeAgent} call with no overhead.
     *
     * <p><b>上下文传播说明：</b>本方法返回的 {@code Mono} 的 {@code deferContextual} 由
     * {@code ToolMethodInvoker} 的 {@code flatMap} 订阅，能正确继承父流式链中的 Reactor Context。
     * 不要在返回 {@link String} 的工具方法内部直接对 {@code Mono} 调用 {@code .block()}，
     * 因为 {@code block()} 会创建独立的订阅，导致 Context 丢失。
     * Context propagation note: this method returns a {@code Mono} whose
     * {@code deferContextual} is subscribed by {@code ToolMethodInvoker}'s {@code flatMap}, which
     * correctly inherits the Reactor Context from the parent streaming chain. Do NOT call
     * {@code .block()} on this Mono directly inside a tool method that returns {@link String},
     * because {@code block()} creates an isolated subscription that loses the Context.
     */
    private Mono<Msg> execLocalSync(
            Agent agent,
            String sessionId,
            String userId,
            String prompt,
            SpawnedAgent spawned,
            RuntimeContext parentCtx) {
        return Mono.deferContextual(
                ctxView -> {
                    if (!ctxView.hasKey(SubagentEventBus.CONTEXT_KEY)) {
                        // Non-streaming path — identical to previous behaviour.
                        System.err.println(
                                "[execLocalSync] NO BUS in context → non-streaming path"
                                        + " agentId="
                                        + spawned.agentId());
                        return agentManager.invokeAgent(agent, sessionId, userId, prompt);
                    }

                    System.err.println(
                            "[execLocalSync] BUS FOUND in context → streaming path agentId="
                                    + spawned.agentId());
                    SubagentEventBus bus = ctxView.get(SubagentEventBus.CONTEXT_KEY);
                    EventSource childSource = buildChildSource(spawned, parentCtx);

                    return agentManager
                            .invokeAgentStream(
                                    agent,
                                    sessionId,
                                    userId,
                                    prompt,
                                    childSource,
                                    StreamOptions.defaults())
                            .doOnNext(
                                    e -> {
                                        log.debug(
                                                "[execLocalSync] forwarding child event to bus:"
                                                        + " type={} msgId={} isLast={}",
                                                e.getType(),
                                                e.getMessage().getId(),
                                                e.isLast());
                                        bus.emit(e);
                                    })
                            .filter(e -> e.isLast() && e.getType() == EventType.AGENT_RESULT)
                            .last()
                            .map(e -> e.getMessage())
                            // If AGENT_RESULT was not included in StreamOptions, the filter above
                            // yields empty; fall back to a plain invokeAgent to get the final Msg.
                            .switchIfEmpty(
                                    Mono.defer(
                                            () ->
                                                    agentManager.invokeAgent(
                                                            agent, sessionId, userId, prompt)));
                });
    }

    /**
     * 为新生成或已知的子代理构建 {@link EventSource}。
     * 路径由父会话 ID（或回退为 {@code "main"}）加子代理的 {@code agentId} 组成，以 {@code "/"} 分隔。
     * Builds an {@link EventSource} for a freshly spawned or known subagent. The path is
     * constructed from the parent session ID (or {@code "main"} as fallback) plus the child's
     * {@code agentId}, separated by {@code "/"}.
     */
    private EventSource buildChildSource(SpawnedAgent spawned, RuntimeContext parentCtx) {
        String parentName =
                (parentCtx != null && parentCtx.getSessionId() != null)
                        ? parentCtx.getSessionId()
                        : "main";
        String path = parentName + "/" + spawned.agentId();
        return EventSource.builder()
                .agentKey(spawned.key())
                .agentId(spawned.agentId())
                .sessionId(spawned.sessionId())
                .depth(spawned.depth())
                .path(path)
                .build();
    }

    /**
     * 通过 {@link TaskRepository} 提交远程任务（以持久化状态），并阻塞直到任务完成或超时。
     * Submits a remote task through {@link TaskRepository} (for durable state) and blocks until
     * it completes or the timeout elapses.
     *
     * <p>使用存储库确保任务对 {@code task_list} 可见，并且能像异步远程任务一样在会话压缩后存活。
     * Using the repository ensures the task is visible to {@code task_list} and survives
     * conversation compaction, just like async remote tasks do.
     */
    private String runRemoteSync(
            RuntimeContext runtimeContext,
            String header,
            String agentId,
            String parentSessionId,
            SubagentDeclaration decl,
            String input,
            long timeoutMs) {
        String taskId = "task_" + UUID.randomUUID();
        TaskRunSpec spec =
                new TaskRunSpec.RemoteTaskRunSpec(decl.getUrl(), decl.getHeaders(), agentId, input);
        BackgroundTask bgTask =
                taskRepository.putTask(runtimeContext, taskId, agentId, parentSessionId, spec);
        try {
            boolean done = bgTask.waitForCompletion(timeoutMs);
            if (!done) {
                return header + "\nstatus: timeout\ntask_id: " + taskId;
            }
            TaskStatus ts = bgTask.getTaskStatus();
            if (ts == TaskStatus.FAILED) {
                Exception err = bgTask.getError();
                String msg = err != null ? err.getMessage() : "remote task failed";
                return header + "\nstatus: error\nerror: " + msg;
            }
            if (ts == TaskStatus.CANCELLED) {
                return header + "\nstatus: cancelled\ntask_id: " + taskId;
            }
            String result = bgTask.getResult();
            return header + "\nstatus: ok\nreply:\n" + (result != null ? result : "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("agent remote sync interrupted: agentId={}", agentId);
            return header + "\nstatus: error\nerror: interrupted";
        }
    }

    private static long resolveTimeoutMs(Integer timeoutSeconds, int defaultSeconds) {
        if (timeoutSeconds == null) {
            return (long) defaultSeconds * 1_000;
        }
        if (timeoutSeconds <= 0) {
            return 0L;
        }
        return (long) Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS) * 1_000;
    }

    private static String formatSpawnInfo(String key, String agentId, String sessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("agent_key: ").append(key).append("\n");
        sb.append("agent_id: ").append(agentId).append("\n");
        sb.append("session_id: ").append(sessionId);
        return sb.toString();
    }
}
