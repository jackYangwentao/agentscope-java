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

import java.io.IOException;
import java.util.Optional;

/**
 * 以 {@link SandboxIsolationKey} 为键的沙箱会话状态持久化和加载的存储抽象。
 * Storage abstraction for persisting and loading sandbox session state keyed by
 * {@link SandboxIsolationKey}.
 *
 * <p>实现必须支持从多线程调用，但不需要为同一键的并发写入提供事务性
 * 或原子性语义。
 *
 * @see WorkspaceSandboxStateStore
 */
public interface SandboxStateStore {

    /**
     * 加载指定键的持久化沙箱状态 JSON。
     * Loads the persisted sandbox state JSON for the given key.
     *
     * @param key 标识状态槽的隔离键
     * @return 序列化的 {@link SandboxState} JSON，如果未存储状态则为空
     * @throws IOException 如果发生存储错误
     */
    Optional<String> load(SandboxIsolationKey key) throws IOException;

    /**
     * 保存指定键的沙箱状态 JSON。
     * Saves the sandbox state JSON for the given key.
     *
     * <p>同一键的现有值将被覆盖。
     *
     * @param key  标识状态槽的隔离键
     * @param json 序列化的 {@link SandboxState} JSON
     * @throws IOException 如果发生存储错误
     */
    void save(SandboxIsolationKey key, String json) throws IOException;

    /**
     * 删除指定键的沙箱状态。
     * Deletes the sandbox state for the given key.
     *
     * <p>如果键未存储状态则为空操作。
     *
     * @param key 标识状态槽的隔离键
     * @throws IOException 如果发生存储错误
     */
    void delete(SandboxIsolationKey key) throws IOException;
}
