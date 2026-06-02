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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import java.util.Collections;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 用于监控和拦截 Agent 执行的 Hook 接口。
 *
 * <p>所有 Agent 执行事件通过单一的 {@link #onEvent(HookEvent)} 方法投递。
 * 该统一事件模型提供了一种干净、类型安全的方式来拦截和修改 Agent 行为。
 *
 * <p><b>Hook 优先级:</b> Hook 按优先级顺序执行(数值越小,优先级越高)。
 * 默认优先级为 100。相同优先级的 Hook 按注册顺序执行。
 *
 * <p><b>事件可修改性:</b> 事件是否可修改通过是否存在 setter 方法来指示:
 * <ul>
 *   <li>有 setter 的事件(如 {@link PreReasoningEvent#setInputMessages})允许修改</li>
 *   <li>无 setter 的事件为通知只读型</li>
 * </ul>
 *
 * <p><b>示例用法:</b>
 *
 * <pre>{@code
 * // 使用默认优先级的基础 Hook
 * Hook loggingHook = new Hook() { ... };
 *
 * // 高优先级 Hook(先执行)
 * Hook authHook = new Hook() { ... };
 *
 * // 修改事件
 * Hook hintInjector = new Hook() { ... };
 * }</pre>
 *
 * <p>Hook interface for monitoring and intercepting agent execution.
 *
 * <p>All agent execution events are delivered through a single {@link #onEvent(HookEvent)} method.
 * This unified event model provides a clean, type-safe way to intercept and modify agent behavior.
 *
 * <p><b>Hook Priority:</b> Hooks are executed in priority order (lower value = higher priority).
 * Default priority is 100. Hooks with the same priority execute in registration order.
 *
 * <p><b>Event Modifiability:</b> Whether an event is modifiable is indicated by the presence of
 * setter methods:
 * <ul>
 *   <li>Events with setters (e.g., {@link PreReasoningEvent#setInputMessages}) allow
 *       modification</li>
 *   <li>Events without setters are notification-only</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * // Basic hook with default priority
 * Hook loggingHook = new Hook() { ... };
 *
 * // High priority hook (executes first)
 * Hook authHook = new Hook() { ... };
 *
 * // Modifying events
 * Hook hintInjector = new Hook() { ... };
 * }</pre>
 *
 * @see HookEvent
 * @see HookEventType
 */
public interface Hook {

    /**
     * 处理 Hook 事件。
     *
     * <p>此方法为所有 Agent 执行事件调用。使用模式匹配来处理特定事件类型。
     *
     * <p><b>可修改事件:</b> 对于有 setter 的事件,可修改上下文,更改将影响 Agent 执行。
     *
     * <p><b>通知事件:</b> 无 setter 的事件为只读。
     *
     * @param event Hook 事件
     * @param <T> 具体事件类型
     * @return 包含可能被修改后事件的 Mono
     *
     * <p>Handle a hook event.
     *
     * <p>This method is called for all agent execution events. Use pattern matching to handle
     * specific event types.
     *
     * <p><b>Modifiable Events:</b> For events with setters, you can modify the context and the
     * changes will affect agent execution.
     *
     * <p><b>Notification Events:</b> Events without setters are read-only.
     *
     * @param event The hook event
     * @param <T> The concrete event type
     * @return Mono containing the potentially modified event
     */
    <T extends HookEvent> Mono<T> onEvent(T event);

    /**
     * 与此 Hook 一起安装的可选工具。
     *
     * <p>在 {@link ReActAgent.Builder#build()} 期间,框架会复制构建器的 {@link Toolkit},
     * 然后将每个 Hook 的 {@code tools()} 列表中的每个非 null 元素
     * 使用 {@link Toolkit#registerTool(Object)} 注册到 Agent 本地的副本。
     *
     * <p>返回 {@link AgentTool} 实例和/或声明了 {@code @Tool} 方法的对象。
     * 默认实现返回空列表,因此现有 Hook 无需更改。
     *
     * <p>如果此方法返回 {@code null},则视为空列表。
     *
     * @return 为此 Hook 注册的工具实例(可能不可变)
     *
     * <p>Optional tools installed together with this hook.
     *
     * <p>During {@link ReActAgent.Builder#build()}, the framework copies the builder {@link
     * Toolkit} and then registers each non-null element from every hook's {@code tools()} list on
     * the agent-local copy using {@link Toolkit#registerTool(Object)}.
     *
     * <p>Return {@link AgentTool} instances and/or objects that declare {@code @Tool} methods.
     * The default implementation returns an empty list so existing hooks need no change.
     *
     * <p>If this method returns {@code null}, it is treated as an empty list.
     *
     * @return tool instances to register for this hook (may be immutable)
     */
    default List<Object> tools() {
        return Collections.emptyList();
    }

    /**
     * 此 Hook 的优先级(数值越小,优先级越高)。
     *
     * <p>Hook 按优先级升序执行。相同优先级的 Hook 按注册顺序执行。
     *
     * <p><b>常见优先级范围:</b>
     * <ul>
     *   <li>0-50: 关键系统 Hook(认证、安全)</li>
     *   <li>51-100: 高优先级 Hook(验证、预处理)</li>
     *   <li>101-500: 普通优先级 Hook(业务逻辑)</li>
     *   <li>501-1000: 低优先级 Hook(日志、监控)</li>
     * </ul>
     *
     * @return 优先级值(默认: 100)
     *
     * <p>The priority of this hook (lower value = higher priority).
     *
     * <p>Hooks are executed in ascending priority order. Hooks with the same priority execute in
     * their registration order.
     *
     * <p><b>Common Priority Ranges:</b>
     * <ul>
     *   <li>0-50: Critical system hooks (auth, security)</li>
     *   <li>51-100: High priority hooks (validation, preprocessing)</li>
     *   <li>101-500: Normal priority hooks (business logic)</li>
     *   <li>501-1000: Low priority hooks (logging, metrics)</li>
     * </ul>
     *
     * @return The priority value (default: 100)
     */
    default int priority() {
        return 100;
    }
}
