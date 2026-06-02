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
package io.agentscope.harness.agent.hook;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.RuntimeContextAware;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Hook that evicts oversized tool results to the {@link AbstractFilesystem} immediately after
 * each tool call, before the result is stored in the agent's memory.
 *
 * <p>When the text content of a {@link ToolResultBlock} exceeds
 * {@link ToolResultEvictionConfig#getMaxResultChars()}, this hook:
 * <ol>
 *   <li>Writes the full result to
 *       {@code {evictionPath}/{agentName}/{sanitized-toolCallId}} in the filesystem.</li>
 *   <li>Replaces the in-context {@code ToolResultBlock} with a compact placeholder containing
 *       a head+tail preview and an instruction to use {@code readFile} for the full content.</li>
 *   <li>Calls {@link PostActingEvent#setToolResult} so downstream hooks and memory see only
 *       the placeholder.</li>
 * </ol>
 *
 * <p><b>Independence from other context-management mechanisms:</b>
 * <ul>
 *   <li><b>This hook</b> fires on {@link PostActingEvent} — once per tool call, triggered by
 *       individual result <em>size</em> (context width).</li>
 *   <li><b>Argument truncation</b> runs inside {@link CompactionHook}
 *       at {@code PreReasoningEvent} — triggered by accumulated message count/tokens.</li>
 *   <li><b>Conversation compaction</b> runs inside {@code CompactionHook} at
 *       {@code PreReasoningEvent} — triggered by overall conversation length (context depth).</li>
 * </ul>
 * Each mechanism evaluates its own independent condition; none depends on the others having run.
 *
 * <p>Runs at priority 50, <em>after</em> {@link AgentTraceHook} (priority 0) so the original
 * result size is logged before the placeholder replaces it.
 *
 * <p>Tools listed in {@link ToolResultEvictionConfig#getExcludedToolNames()} are never evicted
 * (e.g. {@code readFile} — evicting would cause re-read loops).
 *
 * <p>工具结果驱逐钩子，在每次工具调用之后、结果存储到 agent 内存之前，
 * 将过大的工具结果驱逐到 {@link AbstractFilesystem}。
 * 当 ToolResultBlock 的文本内容超过配置的最大字符数时，将完整结果写入文件系统，
 * 并用包含首尾预览和 readFile 使用说明的紧凑占位符替换上下文中的 ToolResultBlock。
 * 与参数截断和对话压缩相互独立，各自评估独立条件。
 */
public class ToolResultEvictionHook implements Hook, RuntimeContextAware {

    private static final Logger log = LoggerFactory.getLogger(ToolResultEvictionHook.class);

    private final AbstractFilesystem filesystem;
    private final ToolResultEvictionConfig config;
    private volatile RuntimeContext runtimeContext;

    public ToolResultEvictionHook(AbstractFilesystem filesystem, ToolResultEvictionConfig config) {
        this.filesystem = filesystem;
        this.config = config;
    }

    @Override
    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    @Override
    public int priority() {
        // After AgentTraceHook (0) — original result size is logged first, then replaced
        // 在 AgentTraceHook (0) 之后 — 先记录原始结果大小，然后替换
        return 50;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (!(event instanceof PostActingEvent postActing)) {
            return Mono.just(event);
        }

        ToolUseBlock toolUse = postActing.getToolUse();
        ToolResultBlock toolResult = postActing.getToolResult();

        if (toolUse == null || toolResult == null) {
            return Mono.just(event);
        }

        String toolName = toolUse.getName();
        if (config.getExcludedToolNames().contains(toolName)) {
            return Mono.just(event);
        }

        String fullText = extractText(toolResult);
        if (fullText.length() <= config.getMaxResultChars()) {
            return Mono.just(event);
        }

        String agentName = event.getAgent().getName();
        String toolCallId = toolUse.getId();
        String evictionPath = buildEvictionPath(agentName, toolCallId);

        try {
            RuntimeContext rc = runtimeContext != null ? runtimeContext : RuntimeContext.empty();
            WriteResult writeResult = filesystem.write(rc, evictionPath, fullText);
            if (!writeResult.isSuccess()) {
                log.warn(
                        "[{}] Failed to evict tool result [tool={}, id={}]: {}",
                        agentName,
                        toolName,
                        toolCallId,
                        writeResult.error());
                return Mono.just(event);
            }

            String placeholder = buildPlaceholder(fullText, evictionPath);
            ToolResultBlock evicted =
                    new ToolResultBlock(
                            toolResult.getId(),
                            toolResult.getName(),
                            List.of(TextBlock.builder().text(placeholder).build()),
                            null);

            postActing.setToolResult(evicted);

            log.info(
                    "[{}] Evicted large tool result [tool={}, id={}, chars={} -> {}]",
                    agentName,
                    toolName,
                    toolCallId,
                    fullText.length(),
                    evictionPath);
        } catch (Exception e) {
            log.warn(
                    "[{}] Exception evicting tool result [tool={}, id={}]: {}",
                    agentName,
                    toolName,
                    toolCallId,
                    e.getMessage());
        }

        return Mono.just(event);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // 辅助方法
    // -------------------------------------------------------------------------

    /**
     * Extracts the concatenated text content from all text blocks in a tool result.
     *
     * <p>从工具结果的所有文本块中提取拼接后的文本内容。
     */
    private String extractText(ToolResultBlock toolResult) {
        if (toolResult.getOutput() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : toolResult.getOutput()) {
            if (block instanceof TextBlock tb && tb.getText() != null) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /**
     * Builds the filesystem path for evicted tool results, sanitizing agent name and tool call ID
     * for filesystem safety.
     *
     * <p>为驱逐的工具结果构建文件系统路径，对 agent 名称和工具调用 ID 进行文件系统安全净化。
     */
    private String buildEvictionPath(String agentName, String toolCallId) {
        String base = config.getEvictionPath();
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        // Sanitize to filesystem-safe characters
        // 净化为文件系统安全的字符
        String safeAgent = agentName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String safeId = toolCallId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return base + "/" + safeAgent + "/" + safeId;
    }

    /**
     * Builds a compact placeholder string with a head+tail preview and an instruction to use
     * readFile for the full content.
     *
     * <p>构建紧凑的占位符字符串，包含首尾预览和使用 readFile 读取完整内容的说明。
     */
    private String buildPlaceholder(String fullText, String evictionPath) {
        int len = fullText.length();
        int pLen = Math.min(config.getPreviewChars(), len / 2);

        StringBuilder sb = new StringBuilder();
        sb.append(
                String.format(
                        "Tool output was too large (%,d chars) and has been saved to `%s`.%n"
                                + "To read the full output, use `read_file` with path `%s`.%n%n",
                        len, evictionPath, evictionPath));

        if (pLen > 0) {
            sb.append(String.format("Preview (first %,d chars):%n", pLen));
            sb.append(fullText, 0, pLen);
            sb.append(String.format("%n%n... and last %,d chars:%n", pLen));
            sb.append(fullText, len - pLen, len);
        }

        return sb.toString();
    }
}
