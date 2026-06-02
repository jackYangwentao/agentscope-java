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

import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;

/**
 * Controls how agent state is isolated and shared across calls.
 *
 * <p>This enum is the canonical isolation-scope definition used by both the sandbox filesystem
 * backend ({@link io.agentscope.harness.agent.sandbox.SandboxContext}) and the remote filesystem
 * backend ({@link RemoteFilesystemSpec}).
 *
 * <p><b>Sandbox semantics</b>: the scope determines which key is used when persisting and loading
 * {@code _sandbox.json} state. Calls that resolve to the <em>same</em> scope key will
 * sequentially reuse the same sandbox (each call resumes the persisted state from the previous
 * one).
 *
 * <p><b>Store namespace semantics</b>: the scope determines the namespace prefix used by
 * {@link RemoteFilesystem} when routing files to the shared
 * key-value store. Different scopes produce different namespace prefixes, controlling which calls
 * share the same view of stored files.
 *
 * <p>Scope selection:
 * <ul>
 *   <li>{@link #SESSION} – isolated per session; the default.</li>
 *   <li>{@link #USER} – shared across all sessions of the same user.</li>
 *   <li>{@link #AGENT} – shared across all users and sessions of the same agent.</li>
 *   <li>{@link #GLOBAL} – globally shared within the same workspace/store instance.</li>
 * </ul>
 *
 * <p><b>Concurrency note:</b> for sandbox mode this is sequential-reuse sharing, not
 * live-instance sharing. Concurrent calls at the same scope each get their own running container;
 * they converge on the last persisted snapshot at the end of the call.
 */
/**
 * 控制代理状态在多次调用之间如何隔离和共享。
 *
 * <p>此枚举是沙箱文件系统后端（{@link io.agentscope.harness.agent.sandbox.SandboxContext}）
 * 和远程文件系统后端（{@link RemoteFilesystemSpec}）使用的规范隔离范围定义。
 *
 * <p><b>沙箱语义：</b>范围决定了持久化和加载 {@code _sandbox.json} 状态时使用哪个键。
 * 解析为<em>相同</em>范围键的调用将顺序重用相同的沙箱（每次调用从上一次调用恢复持久化状态）。
 *
 * <p><b>存储命名空间语义：</b>范围决定了 {@link RemoteFilesystem} 在将文件路由到共享
 * 键值存储时使用的命名空间前缀。不同的范围产生不同的命名空间前缀，控制哪些调用
 * 共享相同的存储文件视图。
 *
 * <p>范围选择：
 * <ul>
 *   <li>{@link #SESSION} – 按会话隔离；默认值。</li>
 *   <li>{@link #USER} – 在同一用户的所有会话之间共享。</li>
 *   <li>{@link #AGENT} – 在同一代理的所有用户和会话之间共享。</li>
 *   <li>{@link #GLOBAL} – 在同一工作区/存储实例内全局共享。</li>
 * </ul>
 *
 * <p><b>并发说明：</b>对于沙箱模式，这是顺序重用共享，而非实时实例共享。
 * 同一范围的并发调用各自拥有自己的运行容器；它们在调用结束时收敛到最后持久化的快照。
 */
public enum IsolationScope {

    /**
     * Isolate by session identifier.
     *
     * <p>This is the default behavior. Each distinct session gets its own sandbox state /
     * store namespace.  If no session key is present in the
     * {@link io.agentscope.core.agent.RuntimeContext}, state lookup is skipped and a fresh
     * sandbox is created (or a default store namespace is used).
     */
    /**
     * 按会话标识符隔离。
     *
     * <p>这是默认行为。每个不同的会话获得自己的沙箱状态/存储命名空间。
     * 如果 {@link io.agentscope.core.agent.RuntimeContext} 中不存在会话键，
     * 则跳过状态查找并创建新的沙箱（或使用默认存储命名空间）。
     */
    SESSION,

    /**
     * Share across all sessions belonging to the same
     * {@link io.agentscope.core.agent.RuntimeContext#getUserId() userId}.
     *
     * <p>If {@code userId} is blank, a warning is logged and state lookup / namespace resolution
     * degrades to the default (fresh sandbox create, or an anonymous-user namespace).
     */
    /**
     * 在同一 {@link io.agentscope.core.agent.RuntimeContext#getUserId() userId}
     * 的所有会话之间共享。
     *
     * <p>如果 {@code userId} 为空，则记录警告，状态查找/命名空间解析降级为默认值
     * （创建新的沙箱，或使用匿名用户命名空间）。
     */
    USER,

    /**
     * Share across all users and sessions of the same agent (identified by agent name).
     *
     * <p>The agent name is fixed at build time and is always available; this scope never
     * degrades due to a missing context field.
     */
    /**
     * 在同一代理（通过代理名称标识）的所有用户和会话之间共享。
     *
     * <p>代理名称在构建时固定且始终可用；此范围不会因缺少上下文字段而降级。
     */
    AGENT,

    /**
     * One shared state / namespace globally within the same workspace store instance.
     *
     * <p>Use with care: all agents and users that share the same store will compete to write
     * the global slot.
     */
    /**
     * 在同一工作区存储实例内全局共享一个状态/命名空间。
     *
     * <p>谨慎使用：共享同一存储的所有代理和用户将竞争写入全局槽位。
     */
    GLOBAL
}
