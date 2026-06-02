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

import io.agentscope.core.message.ToolResultBlock;

/**
 * ToolEmitter 的空实现，静默丢弃所有发送的数据块。
 *
 * <p>当未配置数据块回调时用作默认值，允许工具在不进行空检查的情况下调用 emit()。
 */
public class NoOpToolEmitter implements ToolEmitter {

    /** Singleton instance. */
    public static final NoOpToolEmitter INSTANCE = new NoOpToolEmitter();

    private NoOpToolEmitter() {}

    /**
     * Silently discards the emitted chunk without performing any action.
     *
     * <p>This no-op implementation allows tools to emit chunks without null checks, ensuring
     * consistent behavior even when no actual chunk callback is configured.
     *
     * @param chunk The tool result chunk to discard (ignored)
     */
    @Override
    public void emit(ToolResultBlock chunk) {
        // No-op: silently discard the chunk
    }
}
