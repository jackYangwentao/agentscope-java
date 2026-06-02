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

/**
 * 从 {@link SandboxManager} 获取 {@link Sandbox} 的结果。
 * Result of acquiring a {@link Sandbox} from {@link SandboxManager}.
 *
 * <p>两种所有权模式：Two ownership modes:
 *
 * <ul>
 *   <li><b>自管理</b>（{@code selfManaged=true}）：SDK 创建了沙箱并负责其完整生命周期 —
 *       {@code stop()} + {@code shutdown()} 在每次代理调用后都会调用。</li>
 *   <li><b>用户管理</b>（{@code selfManaged=false}）：调用者注入了预先存在的沙箱；
 *       SDK 仅调用 {@code stop()}，从不调用 {@code shutdown()}。</li>
 * </ul>
 *
 * <p>当配置了 {@link SandboxExecutionGuard} 时，结果还会携带在沙箱恢复/创建之前获取的
 * {@link SandboxLease}。框架在 {@link SandboxManager#release} 完成后关闭它，
 * 以覆盖完整的调用窗口。
 */
public final class SandboxAcquireResult {

    private final Sandbox sandbox;
    private final boolean selfManaged;
    private final SandboxLease lease;

    private SandboxAcquireResult(Sandbox sandbox, boolean selfManaged, SandboxLease lease) {
        this.sandbox = sandbox;
        this.selfManaged = selfManaged;
        this.lease = lease != null ? lease : SandboxLease.noop();
    }

    /** 创建带守卫租约的自管理结果（SDK 拥有完整生命周期）。 */
    public static SandboxAcquireResult selfManaged(Sandbox sandbox, SandboxLease lease) {
        return new SandboxAcquireResult(sandbox, true, lease);
    }

    /** 创建不带守卫的自管理结果（SDK 拥有完整生命周期）。 */
    public static SandboxAcquireResult selfManaged(Sandbox sandbox) {
        return new SandboxAcquireResult(sandbox, true, SandboxLease.noop());
    }

    /** 创建用户管理的结果（调用者拥有生命周期；SDK 仅调用 stop）。 */
    public static SandboxAcquireResult userManaged(Sandbox sandbox) {
        return new SandboxAcquireResult(sandbox, false, SandboxLease.noop());
    }

    public Sandbox getSandbox() {
        return sandbox;
    }

    /** 如果 SDK 拥有完整沙箱生命周期则返回 {@code true}。 */
    public boolean isSelfManaged() {
        return selfManaged;
    }

    /**
     * 返回此次调用持有的 {@link SandboxLease}，如果未配置守卫则返回一个空操作租约。
     * 框架在 {@link SandboxManager#release} 完成后关闭此租约。
     * Returns the {@link SandboxLease} held for this call, or a no-op lease if no guard was
     * configured. The harness closes this lease after {@link SandboxManager#release} completes.
     */
    public SandboxLease getLease() {
        return lease;
    }
}
