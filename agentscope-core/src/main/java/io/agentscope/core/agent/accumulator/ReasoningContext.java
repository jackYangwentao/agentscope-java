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
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reasoning context that manages all state and content accumulation for a single reasoning round.
 *
 * <p>管理单轮推理的全部状态与内容累积的推理上下文。
 *
 * <p>职责:
 * <ul>
 *   <li>累积来自流式响应的各类内容(文本、思考、工具调用)</li>
 *   <li>生成实时流式消息(用于 Hook 通知)</li>
 *   <li>构建最终聚合消息(用于持久化到 memory)</li>
 * </ul>
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Accumulate various content types (text, thinking, tool calls) from streaming responses
 *   <li>Generate real-time streaming messages (for Hook notifications)
 *   <li>Build final aggregated message (for saving to memory)
 * </ul>
 * @hidden
 */
public class ReasoningContext {

    private final String agentName;
    private String messageId;

    private final TextAccumulator textAcc = new TextAccumulator();
    private final ThinkingAccumulator thinkingAcc = new ThinkingAccumulator();
    private final ToolCallsAccumulator toolCallsAcc = new ToolCallsAccumulator();

    private final List<Msg> allStreamedChunks = new ArrayList<>();

    // 累计的 ChatUsage
    private int inputTokens = 0;
    private int outputTokens = 0;
    private double time = 0;

    public ReasoningContext(String agentName) {
        this.agentName = agentName;
    }

    /**
     * 处理一个响应分片,并返回可立即发出的消息列表。
     *
     * <p>策略:
     * <ul>
     *   <li>TextBlock/ThinkingBlock: 立即发出以供实时展示</li>
     *   <li>ToolUseBlock: 累积并立即发出,以支持实时流式传输</li>
     * </ul>
     *
     * @hidden
     * @param chunk 来自模型的响应分片
     * @return 可立即发出的消息列表
     */
    public List<Msg> processChunk(ChatResponse chunk) {
        this.messageId = chunk.getId();

        // 累计 ChatUsage
        ChatUsage usage = chunk.getUsage();
        if (usage != null) {
            inputTokens = usage.getInputTokens();
            outputTokens = usage.getOutputTokens();
            time = usage.getTime();
        }

        List<Msg> streamingMsgs = new ArrayList<>();

        for (ContentBlock block : chunk.getContent()) {
            if (block instanceof TextBlock tb) {
                textAcc.add(tb);

                // 立即发出文本块
                Msg msg = buildChunkMsg(tb);
                streamingMsgs.add(msg);
                allStreamedChunks.add(msg);

            } else if (block instanceof ThinkingBlock tb) {
                thinkingAcc.add(tb);

                // 立即发出思考块
                Msg msg = buildChunkMsg(tb);
                streamingMsgs.add(msg);
                allStreamedChunks.add(msg);

            } else if (block instanceof ToolUseBlock tub) {
                // 累积工具调用,并立即发出以实现实时流式传输
                toolCallsAcc.add(tub);

                // 立即发出 ToolUseBlock 分片,用于实时展示
                // 每个工具调用分片单独发出,支持多个并行工具调用
                // 对于分片(占位符名称,如 "__fragment__"),需要附上正确的工具调用 ID,
                // 以便用户正确拼接分片
                ToolUseBlock outputBlock = enrichToolUseBlockWithId(tub);
                Msg msg = buildChunkMsg(outputBlock);
                streamingMsgs.add(msg);
                allStreamedChunks.add(msg);
            }
        }

        return streamingMsgs;
    }

    /**
     * 构建包含全部内容块的最终推理消息(文本、思考和工具调用合并在一条消息中)。
     *
     * <p>该方法保证单轮推理产生一条可能含多个内容块的消息。
     *
     * <p>策略:
     * <ol>
     *   <li>如有思考内容则加入</li>
     *   <li>如有文本内容则加入</li>
     *   <li>加入所有工具调用</li>
     * </ol>
     *
     * @hidden
     * @return 包含全部块的完整推理消息,无内容时为 null
     */
    public Msg buildFinalMessage() {
        List<ContentBlock> blocks = new ArrayList<>();

        // 先放思考内容
        if (thinkingAcc.hasContent()) {
            blocks.add(thinkingAcc.buildAggregated());
        }

        // 再放文本内容
        if (textAcc.hasContent()) {
            blocks.add(textAcc.buildAggregated());
        }

        // 最后加入所有工具调用
        List<ToolUseBlock> toolCalls = toolCallsAcc.buildAllToolCalls();
        blocks.addAll(toolCalls);

        // 完全没有内容时返回 null
        if (blocks.isEmpty()) {
            return null;
        }

        // 构造携带累计 ChatUsage 的 metadata
        Map<String, Object> metadata = new HashMap<>();
        if (inputTokens > 0 || outputTokens > 0 || time > 0) {
            ChatUsage chatUsage =
                    ChatUsage.builder()
                            .inputTokens(inputTokens)
                            .outputTokens(outputTokens)
                            .time(time)
                            .build();
            metadata.put(MessageMetadataKeys.CHAT_USAGE, chatUsage);
        }

        return Msg.builder()
                .id(messageId)
                .name(agentName)
                .role(MsgRole.ASSISTANT)
                .content(blocks)
                .metadata(metadata)
                .build();
    }

    /**
     * 从内容块构造一条分片消息。
     * @hidden
     */
    private Msg buildChunkMsg(ContentBlock block) {
        return Msg.builder()
                .id(messageId)
                .name(agentName)
                .role(MsgRole.ASSISTANT)
                .content(block)
                .build();
    }

    /**
     * 为 ToolUseBlock 附加正确的工具调用 ID。
     *
     * <p>对于分片(占位符名称,如 {@code "__fragment__"}),原始 block 可能没有正确的 ID。
     * 本方法从累积器中取出 ID,并构造一个带正确 ID 的新 block,以便用户能正确拼接分片。
     *
     * @param block 原始 ToolUseBlock
     * @return 带正确 ID 的 ToolUseBlock
     */
    private ToolUseBlock enrichToolUseBlockWithId(ToolUseBlock block) {
        // 若 block 已有 ID,原样返回
        if (block.getId() != null && !block.getId().isEmpty()) {
            return block;
        }

        // 从累积器取出当前工具调用 ID
        String currentId = toolCallsAcc.getCurrentToolCallId();
        if (currentId == null || currentId.isEmpty()) {
            return block;
        }

        // 构造带正确 ID 的新 block
        return ToolUseBlock.builder()
                .id(currentId)
                .name(block.getName())
                .input(block.getInput())
                .content(block.getContent())
                .metadata(block.getMetadata())
                .build();
    }

    /**
     * 获取已累积的文本内容。
     *
     * @hidden
     * @return 已累积的文本字符串
     */
    public String getAccumulatedText() {
        return textAcc.getAccumulated();
    }

    /**
     * 获取已累积的思考内容。
     *
     * @hidden
     * @return 已累积的思考字符串
     */
    public String getAccumulatedThinking() {
        return thinkingAcc.getAccumulated();
    }

    /**
     * 按 ID 获取已累积的工具调用。
     *
     * <p>如果 ID 为空或未找到对应 builder,会回退到使用最近一次工具调用。
     *
     * @param id 要查找的工具调用 ID
     * @return 已累积的 {@link ToolUseBlock},未找到时为 null
     */
    public ToolUseBlock getAccumulatedToolCall(String id) {
        return toolCallsAcc.getAccumulatedToolCall(id);
    }

    /**
     * 获取所有已累积的工具调用。
     *
     * @return 所有已累积的 {@link ToolUseBlock} 列表
     */
    public List<ToolUseBlock> getAllAccumulatedToolCalls() {
        return toolCallsAcc.getAllAccumulatedToolCalls();
    }

    /**
     * 获取累计的 ChatUsage。
     *
     * @return 累计 token 后的 ChatUsage;无 usage 数据时为 null
     */
    public ChatUsage getChatUsage() {
        if (inputTokens > 0 || outputTokens > 0 || time > 0) {
            return ChatUsage.builder()
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .time(time)
                    .build();
        }
        return null;
    }
}
