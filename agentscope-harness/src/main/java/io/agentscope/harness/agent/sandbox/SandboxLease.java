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
 * 表示在沙箱隔离槽上持有的执行权的句柄。
 * A handle that represents a held execution right on a sandbox isolation slot.
 *
 * <p>由 {@link SandboxExecutionGuard#tryEnter} 返回。框架在 {@link SandboxManager#release}
 * 完成后自动关闭租约（无论调用成功或失败），因此实现无需担心清理顺序。
 *
 * <p>实现必须是幂等的：多次调用 {@link #close()} 必须是安全的。
 */
public interface SandboxLease extends AutoCloseable {

    /**
     * 释放此租约持有的执行权。
     * Releases the execution right held by this lease.
     *
     * <p>不得抛出异常。任何释放侧的错误应由实现在内部记录日志。
     */
    @Override
    void close();

    /**
     * 返回一个空操作租约，其 {@link #close()} 不执行任何操作。
     * 由默认的 {@link SandboxExecutionGuard#noop()} 实现使用。
     * Returns a no-op lease whose {@link #close()} is a no-op. Used by the default
     * {@link SandboxExecutionGuard#noop()} implementation.
     */
    static SandboxLease noop() {
        return NoopSandboxLease.INSTANCE;
    }

    /** Singleton no-op implementation. */
    final class NoopSandboxLease implements SandboxLease {

        static final NoopSandboxLease INSTANCE = new NoopSandboxLease();

        private NoopSandboxLease() {}

        @Override
        public void close() {}
    }
}
