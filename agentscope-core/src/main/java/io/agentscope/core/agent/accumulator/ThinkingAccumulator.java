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
package io.agentscope.core.agent.accumulator;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ThinkingBlock;

/**
 * Thinking content accumulator for accumulating streaming thinking chunks.
 *
 * <p>用于累积流式思考分片的思考内容累积器。该累积器按顺序拼接所有思考分片,
 * 以构建完整的思考内容。
 *
 * <p>This accumulator concatenates all thinking chunks in order to build the complete thinking
 * content.
 * @hidden
 */
public class ThinkingAccumulator implements ContentAccumulator<ThinkingBlock> {

    private final StringBuilder accumulated = new StringBuilder();

    /**
     * 添加一个思考分片到累积器。仅当 block 及其思考内容均非 null 时才追加。
     *
     * @hidden
     */
    @Override
    public void add(ThinkingBlock block) {
        if (block != null && block.getThinking() != null) {
            accumulated.append(block.getThinking());
        }
    }

    /**
     * 检查累积器中是否已有内容。
     *
     * @hidden
     */
    @Override
    public boolean hasContent() {
        return accumulated.length() > 0;
    }

    /**
     * 从已累积的思考分片构建聚合的 ThinkingBlock。如果没有内容则返回 null。
     *
     * @hidden
     */
    @Override
    public ContentBlock buildAggregated() {
        if (!hasContent()) {
            return null;
        }
        return ThinkingBlock.builder().thinking(accumulated.toString()).build();
    }

    /**
     * 重置累积器状态,清空所有已累积的思考内容。
     *
     * @hidden
     */
    @Override
    public void reset() {
        accumulated.setLength(0);
    }

    /**
     * 获取已累积的思考内容。
     *
     * @hidden
     * @return 已累积的思考字符串
     */
    public String getAccumulated() {
        return accumulated.toString();
    }
}
