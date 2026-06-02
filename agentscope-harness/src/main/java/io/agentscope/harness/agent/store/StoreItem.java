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

import java.util.Map;

/**
 * 从 {@link BaseStore} 检索到的单个存储项。
 *
 * @param key 项在其命名空间中的键
 * @param value 项的数据，以字符串键映射的形式存储
 * @param version 单调递增的版本计数器；从 1 开始，每次成功调用 {@link BaseStore#put}
 *     或 {@link BaseStore#putIfVersion} 时递增。值为 {@code 0} 表示版本未知
 *     （例如旧版实现返回的项）
 */
public record StoreItem(String key, Map<String, Object> value, long version) {

    /** 向后兼容构造函数，用于尚未提供版本的代码。 */
    public StoreItem(String key, Map<String, Object> value) {
        this(key, value, 0L);
    }
}
