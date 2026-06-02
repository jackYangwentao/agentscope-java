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
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管理当前调用的 {@link Sandbox} 实例生命周期。
 * Manages the lifecycle of {@link Sandbox} instances for the current call.
 *
 * <p>获取优先级：{@link SandboxContext#getExternalSandbox()} &gt; {@link
 * SandboxContext#getExternalSandboxState()} &gt; 持久化的 {@link SandboxState} &gt; {@link
 * SandboxClient#create}。
 *
 * <p>当配置了 {@link SandboxExecutionGuard} 时，管理器在沙箱恢复/创建之前
 * 为存在的隔离键获取执行 {@link SandboxLease}。
 * 租约由 {@link SandboxAcquireResult} 携带，并在 {@link #release} 后由调用者
 * ({@link io.agentscope.harness.agent.hook.SandboxLifecycleHook}) 关闭，
 * 确保覆盖完整的调用窗口。
 *
 * <p>优先级 1（外部沙箱）和优先级 2（外部沙箱状态）绕过守卫，
 * 因为调用者正在外部管理该沙箱。
 */
public class SandboxManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxManager.class);

    private final SandboxClient<?> client;
    private final SandboxStateStore stateStore;
    private final String agentId;
    private final SandboxExecutionGuard executionGuard;

    public SandboxManager(SandboxClient<?> client, SandboxStateStore stateStore, String agentId) {
        this(client, stateStore, agentId, SandboxExecutionGuard.noop());
    }

    public SandboxManager(
            SandboxClient<?> client,
            SandboxStateStore stateStore,
            String agentId,
            SandboxExecutionGuard executionGuard) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.executionGuard =
                executionGuard != null ? executionGuard : SandboxExecutionGuard.noop();
    }

    public SandboxAcquireResult acquire(
            SandboxContext sandboxContext, RuntimeContext runtimeContext) throws Exception {
        // Priority 1: user-supplied sandbox — guard does not apply
        if (sandboxContext.getExternalSandbox() != null) {
            Sandbox external = sandboxContext.getExternalSandbox();
            log.debug(
                    "[sandbox] Priority 1: using user-managed sandbox: {}",
                    external.getState() != null ? external.getState().getSessionId() : "?");
            return SandboxAcquireResult.userManaged(external);
        }

        // Priority 2: user-supplied state — guard does not apply
        if (sandboxContext.getExternalSandboxState() != null) {
            Sandbox sandbox = client.resume(sandboxContext.getExternalSandboxState());
            log.debug(
                    "[sandbox] Priority 2: resuming from explicit state: {}",
                    sandboxContext.getExternalSandboxState().getSessionId());
            return SandboxAcquireResult.selfManaged(sandbox);
        }

        // Priority 3 / 4: harness-managed — apply guard when a scope key is present
        Optional<SandboxIsolationKey> scopeKey =
                SandboxIsolationKey.resolve(
                        sandboxContext.getIsolationScope(), runtimeContext, agentId);

        SandboxLease lease = SandboxLease.noop();
        if (scopeKey.isPresent()) {
            log.debug("[sandbox] Acquiring execution guard for scope {}", scopeKey.get());
            lease = executionGuard.tryEnter(scopeKey.get());
        }

        try {
            if (scopeKey.isPresent()) {
                try {
                    Optional<String> stateJson = stateStore.load(scopeKey.get());
                    if (stateJson.isPresent()) {
                        log.debug(
                                "[sandbox] Priority 3: resuming from persisted state (scope={})",
                                scopeKey.get());
                        SandboxState state = client.deserializeState(stateJson.get());
                        Sandbox sandbox = client.resume(state);
                        return SandboxAcquireResult.selfManaged(sandbox, lease);
                    }
                } catch (Exception e) {
                    log.warn(
                            "[sandbox] Failed to load persisted state for scope {}, falling through"
                                    + " to fresh create: {}",
                            scopeKey.get(),
                            e.getMessage(),
                            e);
                }
            }

            log.debug("[sandbox] Priority 4: creating new sandbox");
            WorkspaceSpec spec =
                    sandboxContext.getWorkspaceSpec() != null
                            ? sandboxContext.getWorkspaceSpec().copy()
                            : new WorkspaceSpec();

            @SuppressWarnings("unchecked")
            SandboxClient<SandboxClientOptions> typedClient =
                    (SandboxClient<SandboxClientOptions>) client;
            Sandbox sandbox =
                    typedClient.create(
                            spec,
                            sandboxContext.getSnapshotSpec(),
                            sandboxContext.getClientOptions());
            return SandboxAcquireResult.selfManaged(sandbox, lease);

        } catch (Exception e) {
            // Guard must be released if acquire fails — the caller won't see the result
            lease.close();
            throw e;
        }
    }

    public void release(SandboxAcquireResult result) {
        if (result == null) {
            return;
        }
        Sandbox sandbox = result.getSandbox();
        if (sandbox == null) {
            return;
        }

        // User-managed sandboxes (Priority 1) are owned by the caller — the harness must not
        // stop/snapshot or shutdown them, since the caller relies on the sandbox staying alive
        // across multiple acquire/release cycles (e.g. a registry that reuses one container per
        // user across the browser path and successive agent turns).
        if (!result.isSelfManaged()) {
            return;
        }

        try {
            sandbox.stop();
        } catch (Exception e) {
            log.warn("[sandbox] Sandbox stop failed: {}", e.getMessage(), e);
        }

        try {
            sandbox.shutdown();
        } catch (Exception e) {
            log.warn("[sandbox] Sandbox shutdown failed: {}", e.getMessage(), e);
        }
    }

    public void persistState(
            SandboxAcquireResult result,
            SandboxContext sandboxContext,
            RuntimeContext runtimeContext) {
        if (result == null || result.getSandbox() == null) {
            return;
        }
        // User-managed sandboxes carry their own persistence story (the registry owns the
        // lifecycle). Writing through the harness state store would double-track state and
        // could conflict with the caller's snapshot policy.
        if (!result.isSelfManaged()) {
            return;
        }
        SandboxState state = result.getSandbox().getState();
        if (state == null) {
            return;
        }

        Optional<SandboxIsolationKey> scopeKey =
                SandboxIsolationKey.resolve(
                        sandboxContext != null ? sandboxContext.getIsolationScope() : null,
                        runtimeContext,
                        agentId);
        if (scopeKey.isEmpty()) {
            log.debug("[sandbox] No scope key available, skipping state persistence");
            return;
        }

        try {
            String json = client.serializeState(state);
            stateStore.save(scopeKey.get(), json);
            log.debug(
                    "[sandbox] Persisted sandbox state for scope {}: sessionId={}",
                    scopeKey.get(),
                    state.getSessionId());
        } catch (Exception e) {
            log.warn("[sandbox] Failed to persist sandbox state: {}", e.getMessage(), e);
        }
    }

    public void clearState(SandboxContext sandboxContext, RuntimeContext runtimeContext) {
        Optional<SandboxIsolationKey> scopeKey =
                SandboxIsolationKey.resolve(
                        sandboxContext != null ? sandboxContext.getIsolationScope() : null,
                        runtimeContext,
                        agentId);
        if (scopeKey.isEmpty()) {
            return;
        }

        try {
            stateStore.delete(scopeKey.get());
        } catch (Exception e) {
            log.warn("[sandbox] Failed to clear sandbox state: {}", e.getMessage(), e);
        }
    }
}
