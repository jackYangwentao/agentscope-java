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

import io.agentscope.harness.agent.IsolationScope;

/**
 * 沙箱执行槽的可插拔并发守卫。
 * Pluggable concurrency guard for sandbox execution slots.
 *
 * <p>守卫控制给定 {@link SandboxIsolationKey} 允许的最大并发执行数。
 * 默认的 {@link #noop()} 不施加任何限制，保持现有行为。
 *
 * <p>此扩展点主要用于 {@link IsolationScope#USER}、{@link IsolationScope#AGENT} 和
 * {@link IsolationScope#GLOBAL} 作用域，其中多个并发调用者可能在同一持久状态槽上竞争
 * （最后写入者胜出）。提供守卫可以在不改变周边基础设施的情况下序列化此类调用者。
 *
 * <p>实现可以使用的任何后端——JVM 信号量、Redis {@code SET NX} 租约、
 * ZooKeeper、数据库建议锁等——且必须是线程安全的。
 *
 * <h2>生命周期</h2>
 *
 * <p>框架在沙箱获取/恢复之前调用 {@link #tryEnter}，并在 {@link SandboxManager#release}
 * 完成后关闭返回的 {@link SandboxLease}，因此守卫覆盖完整的调用窗口：
 * {@code acquire → start → (call) → stop → release → lease.close()}。
 */
@FunctionalInterface
public interface SandboxExecutionGuard {

    /**
     * 获取指定隔离键的执行权，阻塞直到槽可用或调用线程被中断。
     * Acquires the execution right for the given isolation key, blocking until the slot becomes
     * available or the calling thread is interrupted.
     *
     * <p>必须关闭返回的 {@link SandboxLease} 以释放槽。框架自动处理此操作；
     * 调用者无需显式关闭租约。
     *
     * @param key 标识要保护的沙箱槽的隔离键
     * @return 关闭时释放执行权的租约
     * @throws InterruptedException 如果在等待槽时被中断
     */
    SandboxLease tryEnter(SandboxIsolationKey key) throws InterruptedException;

    /**
     * 返回默认的空操作守卫：始终立即允许执行，返回的 {@link SandboxLease} 是空操作。
     * 这是内置默认值——无需配置。
     * Returns the default no-op guard: execution is always allowed immediately and the returned
     * {@link SandboxLease} is a no-op. This is the built-in default — no configuration required.
     */
    static SandboxExecutionGuard noop() {
        return NoopSandboxExecutionGuard.INSTANCE;
    }

    /** 单例空操作实现。 */
    final class NoopSandboxExecutionGuard implements SandboxExecutionGuard {

        static final NoopSandboxExecutionGuard INSTANCE = new NoopSandboxExecutionGuard();

        private NoopSandboxExecutionGuard() {}

        @Override
        public SandboxLease tryEnter(SandboxIsolationKey key) {
            return SandboxLease.noop();
        }
    }
}
