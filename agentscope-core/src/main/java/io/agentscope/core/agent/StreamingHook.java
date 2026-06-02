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

import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PostSummaryEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.hook.SummaryChunkEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * Internal hook implementation for streaming events.
 *
 * <p>用于流式事件传输的内部 Hook 实现。拦截 Hook 回调并将 {@link Event} 实例
 * 发送到 FluxSink,处理事件过滤和分片模式处理。
 *
 * <p>Intercepts hook callbacks and emits {@link Event} instances to a FluxSink. Handles event
 * filtering and chunk mode processing.
 */
class StreamingHook implements Hook {

    private final FluxSink<Event> sink;
    private final StreamOptions options;

    // 用于增量模式,跟踪每条消息的上一次内容(预留用于将来计算 diff)
    private final Map<String, List<ContentBlock>> previousContent = new HashMap<>();

    /**
     * 创建一个新的流式 Hook。
     *
     * @param sink 用于发送事件的 FluxSink
     * @param options 流式传输的配置选项
     */
    StreamingHook(FluxSink<Event> sink, StreamOptions options) {
        this.sink = sink;
        this.options = options;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent) {
            PostReasoningEvent e = (PostReasoningEvent) event;
            // postReasoning 在流式完成后调用,此时消息已是最后/完整状态
            if (options.shouldStream(EventType.REASONING)
                    && options.shouldIncludeReasoningEmission(false)) {
                emitEvent(EventType.REASONING, e.getReasoningMessage(), true);
            }
            return Mono.just(event);
        } else if (event instanceof ReasoningChunkEvent) {
            ReasoningChunkEvent e = (ReasoningChunkEvent) event;
            // 这是中间分片
            if (options.shouldStream(EventType.REASONING)
                    && options.shouldIncludeReasoningEmission(true)) {
                // 根据 StreamOptions 选择发送增量还是累积内容
                Msg msgToEmit =
                        options.isIncremental() ? e.getIncrementalChunk() : e.getAccumulated();
                emitEvent(EventType.REASONING, msgToEmit, false);
            }
            return Mono.just(event);
        } else if (event instanceof PostActingEvent) {
            PostActingEvent e = (PostActingEvent) event;
            // 工具执行完成
            if (options.shouldStream(EventType.TOOL_RESULT)) {
                Msg toolMsg = createToolMessage(e.getToolResult());
                emitEvent(EventType.TOOL_RESULT, toolMsg, true);
            }
            return Mono.just(event);
        } else if (event instanceof ActingChunkEvent) {
            ActingChunkEvent e = (ActingChunkEvent) event;
            // 中间工具分片
            if (options.shouldStream(EventType.TOOL_RESULT) && options.isIncludeActingChunk()) {
                Msg toolMsg = createToolMessage(e.getChunk());
                emitEvent(EventType.TOOL_RESULT, toolMsg, false);
            }
            return Mono.just(event);
        } else if (event instanceof PostSummaryEvent) {
            PostSummaryEvent e = (PostSummaryEvent) event;
            // 摘要生成完成
            if (options.shouldStream(EventType.SUMMARY)
                    && options.shouldIncludeSummaryEmission(false)) {
                emitEvent(EventType.SUMMARY, e.getSummaryMessage(), true);
            }
            return Mono.just(event);
        } else if (event instanceof SummaryChunkEvent) {
            SummaryChunkEvent e = (SummaryChunkEvent) event;
            // 中间摘要分片
            if (options.shouldStream(EventType.SUMMARY)
                    && options.shouldIncludeSummaryEmission(true)) {
                // 根据 StreamOptions 选择发送增量还是累积内容
                Msg msgToEmit =
                        options.isIncremental() ? e.getIncrementalChunk() : e.getAccumulated();
                emitEvent(EventType.SUMMARY, msgToEmit, false);
            }
            return Mono.just(event);
        }
        return Mono.just(event);
    }

    // ========== 辅助方法 ==========

    /**
     * 从工具结果块创建一条 TOOL 角色的消息。
     *
     * @param toolResultBlock 工具结果或分片
     * @return 包含该结果的 TOOL 角色消息
     */
    private Msg createToolMessage(ToolResultBlock toolResultBlock) {
        return Msg.builder()
                .name("system")
                .role(MsgRole.TOOL)
                .content(List.of(toolResultBlock))
                .build();
    }

    /**
     * 将事件发送到 sink。
     *
     * @param type 事件类型
     * @param msg 消息
     * @param isLast 是否为流中最后/完整的消息
     */
    private void emitEvent(EventType type, Msg msg, boolean isLast) {
        Msg processedMsg = msg;

        // 对于增量模式,目前直接使用 ReasoningChunkEvent 给出的增量分片
        // (留作将来计算 diff 之用)

        // 构造并发送事件
        Event event = new Event(type, processedMsg, isLast);
        sink.next(event);

        // 更新跟踪:非最终消息保存其内容,最终消息清理跟踪记录
        if (!isLast) {
            previousContent.put(msg.getId(), new ArrayList<>(msg.getContent()));
        } else {
            previousContent.remove(msg.getId());
        }
    }
}
