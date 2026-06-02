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
package io.agentscope.harness.agent.store;

import io.agentscope.core.agent.RuntimeContext;
import java.util.List;

/**
 * 在调用时为 {@link BaseStore} 操作生成命名空间元组的工厂。
 *
 * <p>与静态命名空间不同，{@code NamespaceFactory} 在<em>每次</em>存储操作（读、写、ls 等）
 * 时被调用，允许命名空间基于每次调用的 {@link RuntimeContext}（用户 ID、会话 ID）而变化，
 * 而不是基于 Agent 实例上的可变共享状态。
 *
 * <p>示例：
 *
 * <pre>{@code
 * NamespaceFactory factory = rc ->
 *         List.of("sessions", rc.getSessionId(), "filesystem");
 * RemoteFilesystem fs = new RemoteFilesystem(store, factory);
 * }</pre>
 */
@FunctionalInterface
public interface NamespaceFactory {

    /**
     * 返回当前操作上下文的命名空间元组。
     *
     * @param runtimeContext 每次调用的运行时上下文；不应为 {@code null}
     *     （没有真实 RC 的调用者必须传递 {@link RuntimeContext#empty()}）
     * @return 非空、非空的命名空间段列表
     */
    List<String> getNamespace(RuntimeContext runtimeContext);
}
