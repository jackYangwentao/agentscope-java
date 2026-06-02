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
package io.agentscope.core.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identifies the origin of an {@link Event} emitted during streaming agent execution.
 *
 * <p>标识流式 Agent 执行期间发出 {@link Event} 的来源。
 *
 * <p>当子 Agent 在父 Agent 的 {@code stream()} 调用内同步执行时,子 Agent 产出的每条
 * {@link Event} 都携带一个 {@code EventSource},用于编码到产生事件的 Agent 的完整逻辑路径。
 * 这与 LangGraph streaming v2 的 <em>namespace / path</em> 概念类似,便于消费者(UI、
 * 适配器、日志)区分顶层 Agent 与任意嵌套子 Agent 的事件。
 *
 * <p>由顶层(父) Agent 发出的事件 {@code source == null}。
 *
 * <p>When a subagent runs synchronously inside a parent agent's {@code stream()} call, every
 * {@link Event} produced by the subagent carries an {@code EventSource} that encodes the full
 * logical path to the producing agent. This mirrors LangGraph streaming v2's
 * <em>namespace / path</em> concept, allowing consumers (UI, adapters, logging) to distinguish
 * events from the top-level agent vs. any nested subagent.
 *
 * <p>Events emitted by the top-level (parent) agent have {@code source == null}.
 *
 * <h2>Field meanings</h2>
 *
 * <table border="1">
 *   <caption>EventSource fields</caption>
 *   <tr><th>Field</th><th>Meaning</th></tr>
 *   <tr><td>{@code agentKey}</td>
 *       <td>Opaque runtime handle for the spawned agent instance, e.g.
 *       {@code "agent:researcher:uuid"}. This is the value the LLM passes back to
 *       {@code agent_send} to address a previously spawned agent.</td></tr>
 *   <tr><td>{@code agentId}</td>
 *       <td>Registered subagent type identifier (the filename without {@code .md} in the
 *       workspace {@code subagents/} directory), e.g. {@code "researcher"}.</td></tr>
 *   <tr><td>{@code agentName}</td>
 *       <td>Human-readable display name of the subagent, may be null if not set.</td></tr>
 *   <tr><td>{@code sessionId}</td>
 *       <td>Unique session ID for this specific subagent invocation. Stable across
 *       {@code agent_send} follow-ups to the same spawned instance.</td></tr>
 *   <tr><td>{@code parentSessionId}</td>
 *       <td>Session ID of the parent agent that issued the {@code agent_spawn} call.</td></tr>
 *   <tr><td>{@code taskId}</td>
 *       <td>Reserved; non-null only when the subagent runs as a background task (async
 *       streaming, not yet implemented — see extension points below).</td></tr>
 *   <tr><td>{@code depth}</td>
 *       <td>Nesting depth: {@code 1} = direct child of the top-level agent, {@code 2} =
 *       grandchild, and so on.</td></tr>
 *   <tr><td>{@code path}</td>
 *       <td>Slash-separated call hierarchy from root to producer, e.g.
 *       {@code "main/researcher"} or {@code "main/planner/sub-executor"}. Split on
 *       {@code "/"} to recover the hierarchy.</td></tr>
 * </table>
 *
 * <h2>Path convention</h2>
 *
 * <p>The {@code path} field is a slash-separated string like {@code "main/researcher"} or
 * {@code "main/planner/sub-executor"}. The root segment is the parent agent's session ID
 * (or {@code "main"} as fallback); each subsequent segment is the {@code agentId} of the
 * subagent at that depth. Consumers can split on {@code "/"} to reconstruct the call hierarchy.
 *
 * <h2>Extension points (not yet implemented)</h2>
 *
 * <ul>
 *   <li><b>Async task streaming:</b> Populate {@code taskId} when the subagent runs as an
 *       async background task. The task repository can attach a dedicated
 *       {@link SubagentEventBus} to each task so the parent's {@code Flux} accumulates
 *       events even after the original request context ends.</li>
 *   <li><b>Remote subagent streaming:</b> An Agent Protocol {@code GET /tasks/{taskId}/events}
 *       SSE endpoint can deliver events from a remote subagent. Each received event is
 *       stamped with an {@code EventSource} (constructed from the remote agent's metadata)
 *       before being forwarded to the local parent {@code FluxSink}.</li>
 *   <li><b>AG-UI / Web adapter:</b> Consumers can inspect {@code source.path} and
 *       {@code source.depth} to render hierarchical cards or collapsible trace panels,
 *       providing a LangGraph v2 streaming-style experience in the UI.</li>
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * EventSource src = EventSource.builder()
 *     .agentKey("agent:researcher:uuid-1")
 *     .agentId("researcher")
 *     .agentName("ResearcherAgent")
 *     .sessionId("sub-uuid-1")
 *     .parentSessionId("parent-session")
 *     .depth(1)
 *     .path("main/researcher")
 *     .build();
 *
 * // Extend for a grandchild agent:
 * EventSource grandchild = src.withAppendedPath("sub-executor");
 * // grandchild.getDepth() == 2, grandchild.getPath() == "main/researcher/sub-executor"
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EventSource {

    /** 标识被生成 Agent 实例的不透明 key(例如 {@code "agent:researcher:uuid"})。 */
    private final String agentKey;

    /** 已注册的 Agent 类型标识(例如 {@code "researcher"})。 */
    private final String agentId;

    /** 人类可读的 Agent 名称。 */
    private final String agentName;

    /** 本次子 Agent 调用所使用的会话 ID。 */
    private final String sessionId;

    /** 生成该子 Agent 的父 Agent 的会话 ID。 */
    private final String parentSessionId;

    /**
     * 可选的任务 ID,仅当本次子 Agent 调用关联到后台任务时非空
     * (保留供未来异步流式支持使用)。
     */
    private final String taskId;

    /** 嵌套深度:1 = 顶层 Agent 的直接子 Agent,2 = 孙子 Agent,以此类推。 */
    private final int depth;

    /**
     * 从顶层 Agent 到本产生者的、以斜杠分隔的路径,例如 {@code "main/researcher"}。
     */
    private final String path;

    private EventSource(Builder builder) {
        this.agentKey = builder.agentKey;
        this.agentId = builder.agentId;
        this.agentName = builder.agentName;
        this.sessionId = builder.sessionId;
        this.parentSessionId = builder.parentSessionId;
        this.taskId = builder.taskId;
        this.depth = builder.depth;
        this.path = builder.path;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回一个 {@code EventSource},其 {@code path} 追加了 {@code segment},
     * {@code depth} 加 1,其余字段从本实例复制。
     */
    public EventSource withAppendedPath(String segment) {
        String newPath = (path == null || path.isEmpty()) ? segment : path + "/" + segment;
        return builder()
                .agentKey(agentKey)
                .agentId(agentId)
                .agentName(agentName)
                .sessionId(sessionId)
                .parentSessionId(parentSessionId)
                .taskId(taskId)
                .depth(depth + 1)
                .path(newPath)
                .build();
    }

    /** @return 生成的 Agent 实例的 opaque key */
    public String getAgentKey() {
        return agentKey;
    }

    /** @return 已注册的 Agent 类型标识 */
    public String getAgentId() {
        return agentId;
    }

    /** @return 人类可读的 Agent 名称 */
    public String getAgentName() {
        return agentName;
    }

    /** @return 本次子 Agent 调用的会话 ID */
    public String getSessionId() {
        return sessionId;
    }

    /** @return 父 Agent 的会话 ID */
    public String getParentSessionId() {
        return parentSessionId;
    }

    /** @return 后台任务 ID(尚未启用) */
    public String getTaskId() {
        return taskId;
    }

    /** @return 嵌套深度 */
    public int getDepth() {
        return depth;
    }

    /** @return 从根到本产生者的斜杠分隔路径 */
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "EventSource{path='" + path + "', depth=" + depth + ", agentId='" + agentId + "'}";
    }

    /** Builder for {@link EventSource}. */
    public static final class Builder {

        private String agentKey;
        private String agentId;
        private String agentName;
        private String sessionId;
        private String parentSessionId;
        private String taskId;
        private int depth = 1;
        private String path;

        private Builder() {}

        public Builder agentKey(String agentKey) {
            this.agentKey = agentKey;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder parentSessionId(String parentSessionId) {
            this.parentSessionId = parentSessionId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public EventSource build() {
            return new EventSource(this);
        }
    }
}
