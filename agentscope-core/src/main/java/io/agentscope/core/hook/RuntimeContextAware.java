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
package io.agentscope.core.hook;

import io.agentscope.core.agent.RuntimeContext;

/**
 * 为需要当前 per-call {@link RuntimeContext} 的 {@link Hook} 实现提供的可选契约。
 *
 * <p>在 {@code ReActAgent.call(msgs, ctx)} 执行期间,框架会在所有实现了此接口
 * 的已注册 Hook 上设置上下文,并在完成后清除。Hook 可以将引用缓存在字段中,
 * 因为同一个 {@link RuntimeContext} 实例是可变的,在 Hook/工具间共享以进行协调。
 *
 * <p>Optional contract for {@link Hook} implementations that need the current
 * per-call {@link RuntimeContext}.
 *
 * <p>During a {@code ReActAgent.call(msgs, ctx)} execution, the framework sets the context
 * on all registered hooks that implement this interface, and clears it on completion. Hooks may
 * cache the reference in a field, as the same {@link RuntimeContext} instance is mutably shared
 * for cross-hook/tool coordination.
 */
@FunctionalInterface
public interface RuntimeContextAware {

    /**
     * 为当前调用注入运行时上下文,在未执行或调用清除后注入 {@code null}。
     *
     * @param context 当前运行时上下文,或 null
     *
     * <p>Injects the runtime context for the current call, or {@code null} when not executing or
     * when clearing after a call.
     *
     * @param context current runtime context, or null
     */
    void setRuntimeContext(RuntimeContext context);
}
