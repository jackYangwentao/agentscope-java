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

package io.agentscope.core.agent;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * {@link Agent#stream} API 的配置选项。
 *
 * <p>控制要接收的事件类型以及流式内容的投递方式。
 *
 * <p><b>Reasoning 过滤(Issue #265):</b>
 * 某些流式后端会发出两种 reasoning 相关事件:
 * <ul>
 *   <li><b>Reasoning chunks</b>: reasoning 过程中的增量分片</li>
 *   <li><b>Reasoning result</b>: 最终的 consolidated reasoning 输出</li>
 * </ul>
 *
 * <p>当 {@link EventType#REASONING} 启用时, 使用
 * {@link #isIncludeReasoningChunk()} 和 {@link #isIncludeReasoningResult()}
 * 过滤这些 reasoning 相关事件。
 *
 * <p><b>示例用法:</b>
 *
 * <pre>{@code
 * // 仅 reasoning 事件,增量模式
 * StreamOptions options = StreamOptions.builder()
 *     .eventTypes(EventType.REASONING)
 *     .incremental(true)
 *     .build();
 *
 * // Reasoning 事件,但隐藏中间增量,仅保留最终结果
 * StreamOptions options = StreamOptions.builder()
 *     .eventTypes(EventType.REASONING)
 *     .includeReasoningChunk(false)
 *     .includeReasoningResult(true)
 *     .incremental(true)
 *     .build();
 *
 * // 多个特定类型
 * StreamOptions options = StreamOptions.builder()
 *     .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
 *     .incremental(true)
 *     .build();
 * }</pre>
 *
 * <p>Configuration options for the {@link Agent#stream} API.
 *
 * <p>Controls which event types to receive and how streaming content is delivered.
 *
 * <p><b>Reasoning filtering (Issue #265):</b>
 * Some streaming backends emit both:
 * <ul>
 *   <li><b>Reasoning chunks</b>: incremental deltas during the reasoning process</li>
 *   <li><b>Reasoning result</b>: the final consolidated reasoning output</li>
 * </ul>
 *
 * <p>Use {@link #isIncludeReasoningChunk()} and {@link #isIncludeReasoningResult()} to filter
 * these reasoning-related emissions when {@link EventType#REASONING} is enabled.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Only reasoning events, incremental mode
 * StreamOptions options = StreamOptions.builder()
 *     .eventTypes(EventType.REASONING)
 *     .incremental(true)
 *     .build();
 *
 * // Reasoning events, but hide intermediate deltas and only keep the final reasoning result
 * StreamOptions options = StreamOptions.builder()
 *     .eventTypes(EventType.REASONING)
 *     .includeReasoningChunk(false)
 *     .includeReasoningResult(true)
 *     .incremental(true)
 *     .build();
 *
 * // Multiple specific types
 * StreamOptions options = StreamOptions.builder()
 *     .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
 *     .incremental(true)
 *     .build();
 * }</pre>
 */
public class StreamOptions {

    private final Set<EventType> eventTypes;
    private final boolean incremental;

    /**
     * 是否在流式传输中包含 reasoning 过程的增量分片。
     * 若为 false,流实现应过滤掉中间的 reasoning chunk 事件。
     *
     * <p>Whether to include the incremental delta of the reasoning process during streaming.
     * <p>
     * If false, intermediate reasoning chunk emissions should be filtered out by the stream
     * implementation.
     */
    private final boolean includeReasoningChunk;

    /**
     * 是否在响应中包含最终的 consolidated reasoning 输出。
     * 若为 false,流实现应过滤掉最终的 reasoning result 事件。
     *
     * <p>Whether to include the final consolidated reasoning output in the response.
     * <p>
     * If false, final reasoning result emissions should be filtered out by the stream
     * implementation.
     */
    private final boolean includeReasoningResult;

    /**
     * 是否在流式传输中包含工具执行期间的增量分片。
     * 若为 false,流实现应过滤掉中间的 acting chunk 事件。
     *
     * <p>Whether to include the incremental chunks from tool execution during streaming.
     * <p>
     * If false, intermediate acting chunk emissions should be filtered out by the stream
     * implementation.
     */
    private final boolean includeActingChunk;

    /**
     * 是否在流式传输中包含摘要生成期间的增量分片。
     * 若为 false,流实现应过滤掉中间的 summary chunk 事件。
     *
     * <p>Whether to include the incremental chunks from summary generation during streaming.
     * <p>
     * If false, intermediate summary chunk emissions should be filtered out by the stream
     * implementation.
     */
    private final boolean includeSummaryChunk;

    /**
     * 是否在响应中包含最终的 consolidated summary 输出。
     * 若为 false,流实现应过滤掉最终的 summary result 事件。
     *
     * <p>Whether to include the final consolidated summary output in the response.
     * <p>
     * If false, final summary result emissions should be filtered out by the stream
     * implementation.
     */
    private final boolean includeSummaryResult;

    /**
     * 由 Builder 调用的私有构造器。
     *
     * @param builder 包含配置值的 Builder 实例
     *
     * <p>Private constructor called by the builder.
     *
     * @param builder The builder containing configuration values
     */
    private StreamOptions(Builder builder) {
        this.eventTypes = builder.eventTypes;
        this.incremental = builder.incremental;
        this.includeReasoningChunk = builder.includeReasoningChunk;
        this.includeReasoningResult = builder.includeReasoningResult;
        this.includeActingChunk = builder.includeActingChunk;
        this.includeSummaryChunk = builder.includeSummaryChunk;
        this.includeSummaryResult = builder.includeSummaryResult;
    }

    /**
     * 默认选项: 所有事件类型, 增量模式, 包含 reasoning chunk 和 reasoning result。
     *
     * @return 默认配置的 StreamOptions
     *
     * <p>Default options: All event types, incremental mode, include both reasoning chunk and reasoning result.
     *
     * @return StreamOptions with default configuration
     */
    public static StreamOptions defaults() {
        return builder().build();
    }

    /**
     * 创建 StreamOptions 的 Builder。
     *
     * @return 新的 Builder 实例
     *
     * <p>Creates a new builder for StreamOptions.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取应被流式传输的事件类型集合。
     * 若集合包含 {@link EventType#ALL}, 则所有事件类型都会被流式传输。
     *
     * @return 要流式传输的事件类型集合
     *
     * <p>Get the set of event types that should be streamed.
     *
     * <p>If the set contains {@link EventType#ALL}, all event types will be streamed.
     *
     * @return The set of event types to stream
     */
    public Set<EventType> getEventTypes() {
        return eventTypes;
    }

    /**
     * 检查增量模式是否启用。
     * 增量模式下,每次只投递新增内容;累积模式下,每次投递全部累积内容。
     *
     * @return 启用增量模式则返回 true
     *
     * <p>Check if incremental mode is enabled.
     *
     * <p>In incremental mode, only new content is delivered in each emission. In cumulative mode,
     * all accumulated content is delivered.
     *
     * @return true if incremental mode is enabled
     */
    public boolean isIncremental() {
        return incremental;
    }

    /**
     * Reasoning "chunk" 事件是否应被包含。
     * Reasoning chunks 是流式传输中 reasoning 过程的增量分片。
     *
     * @return 应包含 reasoning chunks 则返回 true
     *
     * <p>Whether reasoning "chunk" emissions should be included.
     *
     * <p>Reasoning chunks are the incremental delta of the reasoning process during streaming.</p>
     *
     * @return true if reasoning chunks should be included
     */
    public boolean isIncludeReasoningChunk() {
        return includeReasoningChunk;
    }

    /**
     * 最终的 reasoning result 是否应被包含。
     * Reasoning result 是响应中最终的 consolidated reasoning 输出。
     *
     * @return 应包含最终的 reasoning result 则返回 true
     *
     * <p>Whether the final reasoning result should be included.
     *
     * <p>The reasoning result is the final consolidated reasoning output in the response.</p>
     *
     * @return true if the final reasoning result should be included
     */
    public boolean isIncludeReasoningResult() {
        return includeReasoningResult;
    }

    /**
     * Acting (工具执行) chunk 事件是否应被包含。
     * Acting chunks 是通过 ToolEmitter 产生的工具执行增量输出。
     *
     * @return 应包含 acting chunks 则返回 true
     *
     * <p>Whether acting (tool execution) chunk emissions should be included.
     *
     * <p>Acting chunks are the incremental outputs from tool execution via ToolEmitter.</p>
     *
     * @return true if acting chunks should be included
     */
    public boolean isIncludeActingChunk() {
        return includeActingChunk;
    }

    /**
     * Summary chunk 事件是否应被包含。
     * Summary chunks 是达到最大迭代次数时摘要生成的增量输出。
     *
     * @return 应包含 summary chunks 则返回 true
     *
     * <p>Whether summary chunk emissions should be included.
     *
     * <p>Summary chunks are the incremental outputs from summary generation when max iterations
     * is reached.</p>
     *
     * @return true if summary chunks should be included
     */
    public boolean isIncludeSummaryChunk() {
        return includeSummaryChunk;
    }

    /**
     * 最终的 summary result 是否应被包含。
     * Summary result 是达到最大迭代次数时最终的 consolidated summary 输出。
     *
     * @return 应包含最终的 summary result 则返回 true
     *
     * <p>Whether the final summary result should be included.
     *
     * <p>The summary result is the final consolidated summary output when max iterations
     * is reached.</p>
     *
     * @return true if the final summary result should be included
     */
    public boolean isIncludeSummaryResult() {
        return includeSummaryResult;
    }

    /**
     * 检查指定事件类型是否应被流式传输。
     *
     * @param type 要检查的事件类型
     * @return 该类型应被流式传输则返回 true
     *
     * <p>Check if a specific event type should be streamed.
     *
     * @param type The event type to check
     * @return true if this type should be streamed
     */
    public boolean shouldStream(EventType type) {
        return eventTypes.contains(EventType.ALL) || eventTypes.contains(type);
    }

    /**
     * 流式实现判断是否发出 reasoning 子类型的便捷方法。
     *
     * <p><b>TODO (Issue #265):</b> 将这些标志串联到流式事件映射层中,
     * 即 reasoning 事件转换为 Flux 事件之处(如区分 chunk vs result)。
     *
     * @param isChunk 若为 true 表示 reasoning 事件是增量分片,false 表示是最终结果
     * @return 此 reasoning 事件应被包含则返回 true
     *
     * <p>Convenience method for stream implementations to decide whether to emit a reasoning
     * subtype.
     *
     * <p><b>TODO (Issue #265):</b> Thread these flags through the stream event mapping layer where
     * reasoning events are converted into Flux emissions (e.g., when distinguishing chunk vs
     * result).
     *
     * @param isChunk true if the reasoning emission is an incremental chunk, false if it is the
     *        final result
     * @return true if this reasoning emission should be included
     */
    public boolean shouldIncludeReasoningEmission(boolean isChunk) {
        return isChunk ? includeReasoningChunk : includeReasoningResult;
    }

    /**
     * 流式实现判断是否发出 summary 子类型的便捷方法。
     *
     * @param isChunk 若为 true 表示 summary 事件是增量分片,false 表示是最终结果
     * @return 此 summary 事件应被包含则返回 true
     *
     * <p>Convenience method for stream implementations to decide whether to emit a summary
     * subtype.
     *
     * @param isChunk true if the summary emission is an incremental chunk, false if it is the
     *        final result
     * @return true if this summary emission should be included
     */
    public boolean shouldIncludeSummaryEmission(boolean isChunk) {
        return isChunk ? includeSummaryChunk : includeSummaryResult;
    }

    /**
     * {@link StreamOptions} 的 Builder。
     *
     * <p>Builder for {@link StreamOptions}.
     */
    public static class Builder {
        private Set<EventType> eventTypes = EnumSet.of(EventType.ALL);
        private boolean incremental = true;

        // Defaults are "true" to preserve existing behavior.
        private boolean includeReasoningChunk = true;
        private boolean includeReasoningResult = true;
        private boolean includeActingChunk = true;
        private boolean includeSummaryChunk = true;
        private boolean includeSummaryResult = true;

        /**
         * 设置要流式传输的事件类型。
         * 只有匹配这些类型的事件才会在 Flux 中发出。使用 {@link EventType#ALL} 接收所有类型。
         *
         * @param types 一个或多个事件类型
         * @return 当前 Builder
         *
         * <p>Set which event types to stream.
         *
         * <p>Only events matching these types will be emitted in the Flux. Use {@link
         * EventType#ALL} to receive all types.
         *
         * @param types One or more event types
         * @return this builder
         */
        public Builder eventTypes(EventType... types) {
            this.eventTypes = EnumSet.copyOf(Arrays.asList(types));
            return this;
        }

        /**
         * 设置是否使用增量模式进行流式传输。
         *
         * <p>控制流式内容的投递方式:
         * <ul>
         *   <li>true (增量): 每次只投递新增内容</li>
         *   <li>false (累积): 每次投递全部累积内容</li>
         * </ul>
         *
         * @param incremental true 为增量模式, false 为累积模式
         * @return 当前 Builder
         *
         * <p>Set whether to use incremental mode for streaming content.
         *
         * <p>Controls how streaming content is delivered:
         * <ul>
         *   <li>true (incremental): Only new content in each emission</li>
         *   <li>false (cumulative): All accumulated content in each emission</li>
         * </ul>
         *
         * @param incremental true for incremental mode, false for cumulative mode
         * @return this builder
         */
        public Builder incremental(boolean incremental) {
            this.incremental = incremental;
            return this;
        }

        /**
         * 包含或排除增量 reasoning chunk 事件。
         * 当 {@link EventType#REASONING} 启用时,某些提供者会在模型思考时发出 reasoning 增量。
         * 设为 false 可隐藏这些事件。
         *
         * @param includeReasoningChunk true 包含 chunk 事件, false 过滤掉
         * @return 当前 Builder
         *
         * <p>Include or exclude incremental reasoning chunk emissions.
         *
         * <p>When {@link EventType#REASONING} is enabled, some providers emit reasoning deltas (chunks)
         * as the model thinks. Set to false to hide these.</p>
         *
         * @param includeReasoningChunk true to include chunk emissions, false to filter them out
         * @return this builder
         */
        public Builder includeReasoningChunk(boolean includeReasoningChunk) {
            this.includeReasoningChunk = includeReasoningChunk;
            return this;
        }

        /**
         * 包含或排除最终的 consolidated reasoning result 事件。
         * 当 {@link EventType#REASONING} 启用时,某些提供者会发出最终的 reasoning result。
         * 设为 false 可隐藏它。
         *
         * @param includeReasoningResult true 包含最终的 reasoning result, false 过滤掉
         * @return 当前 Builder
         *
         * <p>Include or exclude the final consolidated reasoning result emission.
         *
         * <p>When {@link EventType#REASONING} is enabled, some providers emit a final reasoning result.
         * Set to false to hide it.</p>
         *
         * @param includeReasoningResult true to include the final reasoning result, false to filter it out
         * @return this builder
         */
        public Builder includeReasoningResult(boolean includeReasoningResult) {
            this.includeReasoningResult = includeReasoningResult;
            return this;
        }

        /**
         * 包含或排除工具执行 chunk 事件。
         * 当 {@link EventType#TOOL_RESULT} 启用时,工具可能通过 ToolEmitter 发出中间 chunk。
         * 设为 false 可隐藏这些,只接收最终的工具结果。
         *
         * @param includeActingChunk true 包含 chunk 事件, false 过滤掉
         * @return 当前 Builder
         *
         * <p>Include or exclude tool execution chunk emissions.
         *
         * <p>When {@link EventType#TOOL_RESULT} is enabled, tools may emit intermediate chunks via
         * ToolEmitter. Set to false to hide these and only receive the final tool result.</p>
         *
         * @param includeActingChunk true to include chunk emissions, false to filter them out
         * @return this builder
         */
        public Builder includeActingChunk(boolean includeActingChunk) {
            this.includeActingChunk = includeActingChunk;
            return this;
        }

        /**
         * 包含或排除 summary chunk 事件。
         * 当 {@link EventType#SUMMARY} 启用时,摘要生成可能发出中间 chunk。
         * 设为 false 可隐藏这些,只接收最终的 summary 结果。
         *
         * @param includeSummaryChunk true 包含 chunk 事件, false 过滤掉
         * @return 当前 Builder
         *
         * <p>Include or exclude summary chunk emissions.
         *
         * <p>When {@link EventType#SUMMARY} is enabled, summary generation may emit intermediate
         * chunks. Set to false to hide these and only receive the final summary result.</p>
         *
         * @param includeSummaryChunk true to include chunk emissions, false to filter them out
         * @return this builder
         */
        public Builder includeSummaryChunk(boolean includeSummaryChunk) {
            this.includeSummaryChunk = includeSummaryChunk;
            return this;
        }

        /**
         * 包含或排除最终的 consolidated summary result 事件。
         * 当 {@link EventType#SUMMARY} 启用时,生成完成后会发出最终的 summary result。
         * 设为 false 可隐藏它。
         *
         * @param includeSummaryResult true 包含最终的 summary result, false 过滤掉
         * @return 当前 Builder
         *
         * <p>Include or exclude the final consolidated summary result emission.
         *
         * <p>When {@link EventType#SUMMARY} is enabled, the final summary result is emitted after
         * generation completes. Set to false to hide it.</p>
         *
         * @param includeSummaryResult true to include the final summary result, false to filter it out
         * @return this builder
         */
        public Builder includeSummaryResult(boolean includeSummaryResult) {
            this.includeSummaryResult = includeSummaryResult;
            return this;
        }

        /**
         * 构建 StreamOptions 实例。
         *
         * @return 新的 StreamOptions
         *
         * <p>Build the StreamOptions instance.
         *
         * @return A new StreamOptions
         */
        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }
}
