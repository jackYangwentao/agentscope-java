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
package io.agentscope.harness.agent.sandbox;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.IsolationScope;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 不可变键，用于唯一标识给定 {@link IsolationScope} 的沙箱状态槽。
 * Immutable key that uniquely identifies a sandbox state slot for a given
 * {@link IsolationScope}.
 *
 * <p>Use {@link #resolve} to obtain a key from a {@link RuntimeContext}. The result is
 * {@link Optional#empty()} when the required context field is absent (e.g., {@code USER} scope
 * without a {@code userId}), meaning state lookup should be skipped.
 */
public final class SandboxIsolationKey {

    private static final Logger log = LoggerFactory.getLogger(SandboxIsolationKey.class);

    static final String GLOBAL_VALUE = "__global__";

    private final IsolationScope scope;
    private final String value;

    private SandboxIsolationKey(IsolationScope scope, String value) {
        this.scope = scope;
        this.value = value;
    }

    /**
     * 从给定的隔离范围、运行时上下文和 agent ID 解析隔离键。
     * Resolves an isolation key from the given scope, runtime context, and agent ID.
     *
     * <p>解析规则：
     * <ul>
     *   <li>{@code SESSION} – 需要非 null 的 {@code sessionKey}；值为
     *       {@code sessionKey.toIdentifier()}。缺失时返回空。</li>
     *   <li>{@code USER} – 需要非空白的 {@code userId}；值为 {@code userId}。
     *       缺失时记录警告并返回空。</li>
     *   <li>{@code AGENT} – 值为 {@code agentId}（始终存在）。</li>
     *   <li>{@code GLOBAL} – 值为 {@value #GLOBAL_VALUE}（始终存在）。</li>
     *   <li>{@code null} scope – 视为 {@code SESSION}。</li>
     * </ul>
     *
     * @param scope     所需的隔离范围；{@code null} 默认为 {@code SESSION}
     * @param ctx       当前调用的运行时上下文；可以为 {@code null}
     * @param agentId   构建时解析的 agent 名称；不能为 null
     * @return 解析后的键，如果缺少必需的上下文字段则返回空
     */
    public static Optional<SandboxIsolationKey> resolve(
            IsolationScope scope, RuntimeContext ctx, String agentId) {
        IsolationScope effective = scope != null ? scope : IsolationScope.SESSION;
        return switch (effective) {
            case SESSION -> {
                if (ctx == null || ctx.getSessionKey() == null) {
                    yield Optional.empty();
                }
                yield Optional.of(
                        new SandboxIsolationKey(
                                IsolationScope.SESSION, ctx.getSessionKey().toIdentifier()));
            }
            case USER -> {
                if (ctx == null || ctx.getUserId() == null || ctx.getUserId().isBlank()) {
                    log.warn(
                            "[sandbox] USER isolation scope requested but userId is absent"
                                    + " — skipping state lookup; a fresh sandbox will be"
                                    + " created");
                    yield Optional.empty();
                }
                yield Optional.of(new SandboxIsolationKey(IsolationScope.USER, ctx.getUserId()));
            }
            case AGENT ->
                    Optional.of(
                            new SandboxIsolationKey(
                                    IsolationScope.AGENT, Objects.requireNonNull(agentId)));
            case GLOBAL ->
                    Optional.of(new SandboxIsolationKey(IsolationScope.GLOBAL, GLOBAL_VALUE));
        };
    }

    /**
     * 返回隔离范围。
     * Returns the isolation scope.
     *
     * @return 隔离范围
     */
    public IsolationScope getScope() {
        return scope;
    }

    /**
     * 返回范围内的区分值（例如会话 ID、用户 ID、agent 名称）。
     * Returns the discriminating value within the scope (e.g. session id, user id, agent name).
     *
     * @return 值字符串
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SandboxIsolationKey that)) return false;
        return scope == that.scope && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, value);
    }

    @Override
    public String toString() {
        return "SandboxIsolationKey{scope=" + scope + ", value='" + value + "'}";
    }
}
