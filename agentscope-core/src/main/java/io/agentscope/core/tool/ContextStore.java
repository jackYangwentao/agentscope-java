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
package io.agentscope.core.tool;

/**
 * 工具执行上下文的存储层抽象。
 *
 * <p>该接口定义了上下文对象的存储契约。支持两种检索模式：
 * <ol>
 *   <li><b>仅按类型</b>：{@code get(Class<T>)} - 适用于单例场景</li>
 *   <li><b>按键+类型</b>：{@code get(String, Class<T>)} - 适用于多实例场景</li>
 * </ol>
 *
 * <p>这种设计允许同时处理简单情况（一个 UserContext）和复杂情况
 * （不同用户的多个 UserContext 实例）。
 *
 * <p>实现方式可以是：
 * <ul>
 *   <li>基于内存的简单 Map 存储（{@link DefaultContextStore}）</li>
 *   <li>自定义存储后端（Redis、数据库等）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // Single instance per type
 * DatabaseConfig config = store.get(DatabaseConfig.class);
 *
 * // Multiple instances of same type
 * UserContext admin = store.get("admin", UserContext.class);
 * UserContext guest = store.get("guest", UserContext.class);
 * }</pre>
 *
 * @see ToolExecutionContext
 * @see DefaultContextStore
 */
public interface ContextStore {

    /**
     * Retrieves an object by key and type.
     *
     * <p>This method allows storing multiple instances of the same type with different keys.
     * Keys can be user IDs, session IDs, or any other identifier that distinguishes instances.
     *
     * <p>Example:
     * <pre>{@code
     * // Store multiple UserContext instances
     * store.register("user123", new UserContext("user123"));
     * store.register("user456", new UserContext("user456"));
     *
     * // Retrieve specific instance
     * UserContext user123 = store.get("user123", UserContext.class);
     * }</pre>
     *
     * @param key The key identifying the specific instance
     * @param type The class type to retrieve
     * @param <T> The object type
     * @return The object instance, or null if not found
     */
    <T> T get(String key, Class<T> type);

    /**
     * Retrieves an object by type only (without key).
     *
     * <p>This is a convenience method for singleton scenarios where only one instance of a type
     * exists. If multiple instances exist, implementations may:
     * <ul>
     *   <li>Return the "default" instance (implementation-defined)</li>
     *   <li>Return the first registered instance</li>
     *   <li>Return null and require explicit key</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * // Single DatabaseConfig instance
     * DatabaseConfig config = store.get(DatabaseConfig.class);
     * }</pre>
     *
     * @param type The class type to retrieve
     * @param <T> The object type
     * @return The object instance, or null if not found
     */
    <T> T get(Class<T> type);

    /**
     * Checks whether an object with the specified key and type exists.
     *
     * @param key The key identifying the instance
     * @param type The class type to check
     * @return true if the object exists, false otherwise
     */
    boolean contains(String key, Class<?> type);

    /**
     * Checks whether any object of the specified type exists (regardless of key).
     *
     * @param type The class type to check
     * @return true if at least one object of this type exists, false otherwise
     */
    boolean contains(Class<?> type);
}
