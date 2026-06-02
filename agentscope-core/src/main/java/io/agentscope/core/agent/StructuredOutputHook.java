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

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.model.ToolChoice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 使用 HITL 机制处理结构化输出生成的 Hook。
 *
 * <p>此 Hook 拦截 Agent 事件,确保模型调用 {@code generate_response} 工具来生成结构化输出:
 *
 * <ul>
 *   <li><b>PreReasoning:</b> 在 TOOL_CHOICE 模式下强制 tool_choice 为 generate_response</li>
 *   <li><b>PostReasoning:</b> 检查是否调用了 generate_response;若没有则触发重试并加入提醒消息</li>
 *   <li><b>PostActing:</b> 当 generate_response 成功完成时停止 Agent</li>
 *   <li><b>PostCall:</b> 压缩 memory,移除中间的结构化输出消息</li>
 * </ul>
 *
 * <p>Hook for handling structured output generation using the HITL mechanism.
 *
 * <p>This hook intercepts agent events to ensure the model calls the {@code generate_response}
 * tool for structured output generation:
 *
 * <ul>
 *   <li><b>PreReasoning:</b> In TOOL_CHOICE mode, forces tool_choice to generate_response</li>
 *   <li><b>PostReasoning:</b> Checks if generate_response was called; if not, triggers retry
 *       with a reminder message</li>
 *   <li><b>PostActing:</b> Stops the agent when generate_response completes successfully</li>
 *   <li><b>PostCall:</b> Compresses memory by removing intermediate structured output messages</li>
 * </ul>
 *
 * @hidden
 */
public class StructuredOutputHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputHook.class);

    /** 结构化输出生成的工具名称。The tool name for structured output generation. */
    public static final String TOOL_NAME = "generate_response";

    private static final int MAX_RETRIES = 3;

    private final StructuredOutputReminder reminderMode;
    private final GenerateOptions baseOptions;
    private final Memory memory;

    private boolean completed = false;
    private Msg resultMsg = null;
    private int retryCount = 0;
    private ChatUsage aggregatedUsage = null;
    private ThinkingBlock aggregatedThinking = null;

    /**
     * 创建新的 StructuredOutputHook。
     *
     * @param reminderMode 提醒模式(TOOL_CHOICE 或 PROMPT)
     * @param baseOptions 基础生成选项
     * @param memory 用于 PostCall 压缩的 Memory
     *
     * <p>Creates a new StructuredOutputHook.
     *
     * @param reminderMode The reminder mode (TOOL_CHOICE or PROMPT)
     * @param baseOptions The base generation options
     * @param memory The memory for compression in PostCall
     */
    public StructuredOutputHook(
            StructuredOutputReminder reminderMode, GenerateOptions baseOptions, Memory memory) {
        this.reminderMode = reminderMode;
        this.baseOptions = baseOptions;
        this.memory = memory;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreReasoningEvent e) {
            handlePreReasoning(e);
        } else if (event instanceof PostReasoningEvent e) {
            handlePostReasoning(e);
        } else if (event instanceof PostActingEvent e) {
            handlePostActing(e);
        } else if (event instanceof PostCallEvent e) {
            handlePostCall(e);
        }
        return Mono.just(event);
    }

    /**
     * 处理 PreReasoning 事件:在 TOOL_CHOICE 模式下,仅在处理 TOOL_CHOICE 提醒消息时强制 tool_choice。
     */
    private void handlePreReasoning(PreReasoningEvent event) {
        // In TOOL_CHOICE mode, only force tool_choice when processing a TOOL_CHOICE reminder
        // message
        if (reminderMode == StructuredOutputReminder.TOOL_CHOICE) {
            List<Msg> inputMessages = event.getInputMessages();
            if (inputMessages == null || inputMessages.isEmpty()) {
                return;
            }
            Msg lastMsg = inputMessages.get(inputMessages.size() - 1);
            if (lastMsg != null && isToolChoiceReminderMessage(lastMsg)) {
                GenerateOptions options =
                        GenerateOptions.mergeOptions(
                                GenerateOptions.builder()
                                        .toolChoice(new ToolChoice.Specific(TOOL_NAME))
                                        .build(),
                                baseOptions);
                event.setGenerateOptions(options);
                log.debug("Set tool_choice to force generate_response on retry");
            }
        }
    }

    /**
     * 检查消息是否为 TOOL_CHOICE 提醒消息。
     */
    private boolean isToolChoiceReminderMessage(Msg msg) {
        Map<String, Object> metadata = msg.getMetadata();
        if (metadata == null) {
            return false;
        }
        return StructuredOutputReminder.TOOL_CHOICE
                .toString()
                .equals(metadata.get(MessageMetadataKeys.STRUCTURED_OUTPUT_REMINDER_TYPE));
    }

    /**
     * 处理 PostReasoning 事件:检查是否调用了 generate_response;若没有则触发重试。
     *
     * <p>Handle PostReasoning: checks if generate_response was called; if not, triggers retry.
     */
    private void handlePostReasoning(PostReasoningEvent event) {
        Msg msg = event.getReasoningMessage();
        if (msg == null) {
            return;
        }

        boolean hasCall = !msg.getContentBlocks(ToolUseBlock.class).isEmpty();

        if (!hasCall && retryCount < MAX_RETRIES) {
            retryCount++;
            log.debug(
                    "Model didn't call any tool, requesting retry ({}/{})",
                    retryCount,
                    MAX_RETRIES);

            // Add reminder message and goto reasoning
            event.gotoReasoning(createReminderMessage(reminderMode));
        }
        // If max retries exceeded, let it continue to summarizing which will report error
    }

    /**
     * 处理 PostActing 事件:当 generate_response 成功完成时停止 Agent 并收集元数据。
     *
     * <p>Handle PostActing: stops the agent and collects metadata when generate_response succeeds.
     */
    private void handlePostActing(PostActingEvent event) {
        ToolUseBlock toolUse = event.getToolUse();
        if (toolUse != null && TOOL_NAME.equals(toolUse.getName())) {
            ToolResultBlock result = event.getToolResult();
            if (result != null
                    && result.getMetadata() != null
                    && Boolean.TRUE.equals(result.getMetadata().get("success"))) {
                completed = true;
                resultMsg = event.getToolResultMsg();

                // Collect metadata now, before memory compression (which happens in PostCall)
                List<Msg> messages = new ArrayList<>(memory.getMessages());
                collectStructuredOutputMetadata(messages);

                log.debug("generate_response completed successfully, stopping agent");
                event.stopAgent();
            }
        }
    }

    /**
     * 处理 PostCall 事件:在结构化输出完成时压缩 memory。
     *
     * <p>Handle PostCall: compresses memory when structured output completes.
     */
    private void handlePostCall(PostCallEvent event) {
        if (!completed) {
            return;
        }
        // 压缩 memory:移除 generate_response 相关的中间消息
        // Compress memory: remove generate_response related intermediate messages
        compressMemory();
    }

    /**
     * 从 memory 中移除结构化输出相关的消息,并添加最终响应。
     *
     * <p>Remove structured output related messages from memory and add final response.
     */
    private void compressMemory() {
        List<Msg> original = new ArrayList<>(memory.getMessages());
        int originalSize = original.size();

        memory.clear();
        for (Msg msg : original) {
            if (!isStructuredOutputRelated(msg)) {
                memory.addMessage(msg);
            }
        }

        // 添加最终响应消息(从 resultMsg 中提取)
        // Add the final response message (extracted from resultMsg)
        if (resultMsg != null) {
            Msg finalMsg = extractFinalResponseMsg(resultMsg);
            if (finalMsg != null) {
                // 将收集的元数据合并到最终消息中
                // Merge collected metadata into final message
                finalMsg = mergeCollectedMetadata(finalMsg);
                memory.addMessage(finalMsg);
            }
        }

        log.debug(
                "Memory compressed: {} -> {} messages", originalSize, memory.getMessages().size());
    }

    /**
     * 从正在被移除的 assistant 消息中收集并聚合元数据。
     *
     * <p>Collect and aggregate metadata from assistant messages that are being removed.
     */
    private void collectStructuredOutputMetadata(List<Msg> messages) {
        int totalInput = 0;
        int totalOutput = 0;
        double totalTime = 0;
        boolean hasUsage = false;

        for (Msg msg : messages) {
            if (isStructuredOutputRelated(msg) && msg.getRole() == MsgRole.ASSISTANT) {
                // 收集 ChatUsage
                // Collect ChatUsage
                ChatUsage usage = msg.getChatUsage();
                if (usage != null) {
                    hasUsage = true;
                    totalInput += usage.getInputTokens();
                    totalOutput += usage.getOutputTokens();
                    totalTime += usage.getTime();
                }

                // 收集 ThinkingBlock(保留最后一个)
                // Collect ThinkingBlock (keep the last one)
                ThinkingBlock thinking = msg.getFirstContentBlock(ThinkingBlock.class);
                if (thinking != null) {
                    this.aggregatedThinking = thinking;
                }
            }
        }

        this.aggregatedUsage =
                hasUsage
                        ? ChatUsage.builder()
                                .inputTokens(totalInput)
                                .outputTokens(totalOutput)
                                .time(totalTime)
                                .build()
                        : null;
    }

    /**
     * 将收集的元数据(ChatUsage 和 ThinkingBlock)合并到消息中。
     *
     * <p>Merge collected metadata (ChatUsage and ThinkingBlock) into the message.
     */
    private Msg mergeCollectedMetadata(Msg msg) {
        // 合并 ChatUsage 到 metadata
        // Merge ChatUsage into metadata
        Map<String, Object> metadata =
                new HashMap<>(msg.getMetadata() != null ? msg.getMetadata() : Map.of());
        if (aggregatedUsage != null) {
            metadata.put(MessageMetadataKeys.CHAT_USAGE, aggregatedUsage);
        }

        // 合并 ThinkingBlock 到内容
        // Merge ThinkingBlock into content
        List<ContentBlock> newContent;
        if (aggregatedThinking != null) {
            newContent = new ArrayList<>();
            newContent.add(aggregatedThinking);
            if (msg.getContent() != null) {
                newContent.addAll(msg.getContent());
            }
        } else {
            newContent = msg.getContent();
        }

        return Msg.builder()
                .id(msg.getId())
                .name(msg.getName())
                .role(msg.getRole())
                .content(newContent)
                .metadata(metadata)
                .timestamp(msg.getTimestamp())
                .build();
    }

    /**
     * 从工具结果消息中提取最终响应消息。
     *
     * @param toolResultMsg 包含响应的工具结果消息
     * @return 最终的响应消息,未找到则返回 null
     *
     * <p>Extract the final response message from the tool result message.
     *
     * @param toolResultMsg The tool result message containing the response
     * @return The final response message, or null if not found
     */
    private Msg extractFinalResponseMsg(Msg toolResultMsg) {
        List<ToolResultBlock> toolResults = toolResultMsg.getContentBlocks(ToolResultBlock.class);
        for (ToolResultBlock result : toolResults) {
            if (result.getMetadata() != null
                    && Boolean.TRUE.equals(result.getMetadata().get("success"))
                    && result.getMetadata().containsKey("response_msg")) {
                Object responseMsgObj = result.getMetadata().get("response_msg");
                if (responseMsgObj instanceof Msg responseMsg) {
                    return responseMsg;
                }
            }
        }
        return null;
    }

    /**
     * 检查消息是否与结构化输出相关,应被移除。
     *
     * <p>Check if a message is related to structured output and should be removed.
     */
    private boolean isStructuredOutputRelated(Msg msg) {
        // 提醒消息通过 metadata 标记
        // Reminder messages are marked with metadata
        if (hasReminderMetadata(msg)) {
            return true;
        }

        // ToolUse/ToolResult 按工具名称匹配
        return hasGenerateResponseTool(msg);
    }

    /**
     * 检查消息是否包含结构化输出提醒元数据。
     */
    private boolean hasReminderMetadata(Msg msg) {
        Map<String, Object> metadata = msg.getMetadata();
        return metadata != null
                && Boolean.TRUE.equals(
                        metadata.get(MessageMetadataKeys.STRUCTURED_OUTPUT_REMINDER));
    }

    /**
     * 检查消息是否包含 generate_response 工具的调用或结果。
     */
    private boolean hasGenerateResponseTool(Msg msg) {
        // 检查 ToolUse
        // Check ToolUse
        if (msg.getContentBlocks(ToolUseBlock.class).stream()
                .anyMatch(tu -> TOOL_NAME.equals(tu.getName()))) {
            return true;
        }

        // 检查 ToolResult(所有结果必须匹配,避免误删混合结果)
        // Check ToolResult (all must match to avoid removing mixed results)
        List<ToolResultBlock> results = msg.getContentBlocks(ToolResultBlock.class);
        return !results.isEmpty()
                && results.stream().allMatch(tr -> TOOL_NAME.equals(tr.getName()));
    }

    /**
     * 创建提醒消息,提示模型调用 generate_response。
     *
     * <p>消息包含标记其身份的 metadata 和提醒模式,供 {@link #handlePreReasoning}
     * 判断是否要在重试时强制 tool_choice。
     *
     * @param mode 结构化输出提醒模式
     * @return 携带适当 metadata 的提醒消息
     *
     * <p>Creates a reminder message to prompt the model to call generate_response.
     *
     * <p>The message includes metadata to identify it as a reminder and store the
     * reminder mode, which is used by {@link #handlePreReasoning} to determine
     * whether to force tool_choice on retry.
     *
     * @param mode The structured output reminder mode
     * @return A reminder message with appropriate metadata
     */
    private Msg createReminderMessage(StructuredOutputReminder mode) {
        Map<String, Object> metadata =
                Map.of(
                        MessageMetadataKeys.STRUCTURED_OUTPUT_REMINDER,
                        true,
                        MessageMetadataKeys.STRUCTURED_OUTPUT_REMINDER_TYPE,
                        mode.toString());

        return Msg.builder()
                .name("system")
                .role(MsgRole.USER)
                .content(
                        TextBlock.builder()
                                .text(
                                        "Please call the 'generate_response' function to provide"
                                                + " your response.")
                                .build())
                .metadata(metadata)
                .build();
    }

    /**
     * 检查结构化输出生成是否已完成。
     *
     * @return 成功完成则返回 true
     *
     * <p>Check if structured output generation is completed.
     *
     * @return true if completed successfully
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * 获取 generate_response 的结果消息。
     *
     * @return 结果消息,未完成则返回 null
     *
     * <p>Get the result message from generate_response.
     *
     * @return The result message, or null if not completed
     */
    public Msg getResultMsg() {
        return resultMsg;
    }

    /**
     * 获取所有 reasoning 轮的聚合 ChatUsage。
     *
     * @return 聚合后的 ChatUsage,未收集则返回 null
     *
     * <p>Get the aggregated ChatUsage from all reasoning rounds.
     *
     * @return The aggregated ChatUsage, or null if no usage was collected
     */
    public ChatUsage getAggregatedUsage() {
        return aggregatedUsage;
    }

    /**
     * 获取最后一个 reasoning 轮的 ThinkingBlock。
     *
     * @return ThinkingBlock,未收集则返回 null
     *
     * <p>Get the aggregated ThinkingBlock from the last reasoning round.
     *
     * @return The ThinkingBlock, or null if no thinking was collected
     */
    public ThinkingBlock getAggregatedThinking() {
        return aggregatedThinking;
    }

    @Override
    public int priority() {
        // 高优先级,在其他 Hook 之前执行
        // High priority to execute before other hooks
        return 50;
    }
}
