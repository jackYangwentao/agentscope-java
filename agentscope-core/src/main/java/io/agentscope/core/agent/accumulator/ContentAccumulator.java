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

/**
 * Content accumulator interface for accumulating streaming content blocks.
 *
 * <p>用于累积流式内容块的内容累积器接口。
 * 本接口定义了对来自流式响应的内容块进行累积的契约。不同的内容类型
 * (文本、思考、工具调用)有不同的累积策略。
 *
 * <p>This interface defines the contract for accumulating content blocks from streaming responses.
 * Different content types (text, thinking, tool calls) have different accumulation strategies.
 *
 * @hidden
 * @param <T> 要累积的内容块类型
 */
public interface ContentAccumulator<T extends ContentBlock> {

    /**
     * 向累积器中添加一个内容块分片。
     *
     * @hidden
     * @param block 要添加的内容块分片
     */
    void add(T block);

    /**
     * 检查累积器是否已有任何已累积的内容。
     *
     * @hidden
     * @return 如果有已累积的内容则返回 true,否则返回 false
     */
    boolean hasContent();

    /**
     * 从所有已累积的分片构建聚合后的内容块。
     *
     * @hidden
     * @return 聚合后的内容块,如果没有内容则返回 null
     */
    ContentBlock buildAggregated();

    /**
     * 重置累积器状态,清空所有已累积的内容。
     *
     * @hidden
     */
    void reset();
}
