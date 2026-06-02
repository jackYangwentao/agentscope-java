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
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.util.JsonUtils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool calls accumulator for accumulating streaming tool call chunks.
 *
 * <p>用于累积流式工具调用分片的工具调用累积器。
 *
 * <p>该累积器支持多个并行工具调用,并处理:
 * <ul>
 *   <li>工具名称和 ID 的累积</li>
 *   <li>增量参数合并</li>
 *   <li>原始 JSON 内容的累积与解析</li>
 *   <li>占位符名称处理(例如 {@code "__fragment__"})</li>
 * </ul>
 *
 * <p>This accumulator supports multiple parallel tool calls and handles:
 *
 * <ul>
 *   <li>Tool name and ID accumulation
 *   <li>Incremental parameter merging
 *   <li>Raw JSON content accumulation and parsing
 *   <li>Placeholder name handling (e.g., "__fragment__")
 * </ul>
 * @hidden
 */
public class ToolCallsAccumulator implements ContentAccumulator<ToolUseBlock> {

    // 用于支持多个并行工具调用
    // Key: 工具标识(ID、名称或 index)
    private final Map<String, ToolCallBuilder> builders = new LinkedHashMap<>();
    private int nextIndex = 0;

    // 跟踪最近一次工具调用的 key,用于处理没有 ID 的流式分片
    // 当模型返回带占位符名称且 ID 为空的分片时需要使用
    private String lastToolCallKey = null;

    /** 单个工具调用的内部构建器。 */
    private static class ToolCallBuilder {
        String toolId;
        String name;
        Map<String, Object> args = new HashMap<>();
        StringBuilder rawContent = new StringBuilder();
        Map<String, Object> metadata = new HashMap<>();

        void merge(ToolUseBlock block) {
            // 出现 ID 时更新 toolId
            if (this.toolId == null && block.getId() != null && !block.getId().isEmpty()) {
                this.toolId = block.getId();
            }

            // 出现名称时更新 name(忽略占位符)
            if (block.getName() != null && !isPlaceholder(block.getName())) {
                this.name = block.getName();
            }

            // 合并参数
            if (block.getInput() != null) {
                this.args.putAll(block.getInput());
            }

            // 累积原始内容(用于解析完整 JSON)
            if (block.getContent() != null) {
                this.rawContent.append(block.getContent());
            }

            // 合并元数据(例如 Gemini 3 Pro 的 thoughtSignature)
            if (block.getMetadata() != null && !block.getMetadata().isEmpty()) {
                this.metadata.putAll(block.getMetadata());
            }
        }

        ToolUseBlock build() {
            Map<String, Object> finalArgs = new HashMap<>(args);
            String rawContentStr = this.rawContent.toString();

            // 若没有已解析参数但有原始 JSON 内容,则尝试解析
            if (finalArgs.isEmpty() && rawContentStr.length() > 0) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed =
                            JsonUtils.getJsonCodec().fromJson(rawContentStr, Map.class);
                    if (parsed != null) {
                        finalArgs.putAll(parsed);
                    }
                } catch (Exception ignored) {
                    // 解析失败,保留空 args
                }
            }

            // 在把 rawContent 用作 content 前,始终校验它是否为合法 JSON 对象。
            // 防止在流被中途打断时持久化畸形的 JSON 片段。
            String contentStr;
            if (rawContentStr.isEmpty()) {
                contentStr = "{}";
            } else if (JsonUtils.isValidJsonObject(rawContentStr)) {
                contentStr = rawContentStr;
            } else {
                contentStr = "{}";
            }

            return ToolUseBlock.builder()
                    .id(toolId != null ? toolId : generateId())
                    .name(name)
                    .input(finalArgs)
                    .content(contentStr)
                    .metadata(metadata.isEmpty() ? null : metadata)
                    .build();
        }

        private boolean isPlaceholder(String name) {
            // 常见占位符名称
            return "__fragment__".equals(name)
                    || "__pending__".equals(name)
                    || (name != null && name.startsWith("__"));
        }

        private String generateId() {
            return "tool_call_" + System.currentTimeMillis();
        }
    }

    /**
     * @hidden
     */
    @Override
    public void add(ToolUseBlock block) {
        if (block == null) {
            return;
        }

        // 判断该分片属于哪个工具调用
        String key = determineKey(block);

        // 获取或创建对应的 builder
        ToolCallBuilder builder = builders.computeIfAbsent(key, k -> new ToolCallBuilder());

        // 合并分片
        builder.merge(block);
    }

    /**
     * 为工具调用确定 key(用于区分多个并行调用)。
     *
     * <p>优先级:
     * <ol>
     *   <li>使用工具 ID(若存在且非空)</li>
     *   <li>使用工具名称(若存在且非占位符)</li>
     *   <li>若是分片(占位符名称)且存在上次 key,则复用</li>
     *   <li>否则对没有标识的分片使用递增 index</li>
     * </ol>
     */
    private String determineKey(ToolUseBlock block) {
        // 1. 优先使用工具 ID
        if (block.getId() != null && !block.getId().isEmpty()) {
            String key = block.getId();
            // 记录非占位符的 key
            if (block.getName() != null && !isPlaceholder(block.getName())) {
                lastToolCallKey = key;
            }
            return key;
        }

        // 2. 使用工具名称(非占位符)
        if (block.getName() != null && !isPlaceholder(block.getName())) {
            String key = "name:" + block.getName();
            lastToolCallKey = key;
            return key;
        }

        // 3. 分片(占位符名称)且存在上次 key,复用之
        if (isPlaceholder(block.getName()) && lastToolCallKey != null) {
            return lastToolCallKey;
        }

        // 4. 无任何标识的分片使用递增 index
        String key = "index:" + nextIndex++;
        lastToolCallKey = key;
        return key;
    }

    private boolean isPlaceholder(String name) {
        return name != null && name.startsWith("__");
    }

    /**
     * @hidden
     */
    @Override
    public boolean hasContent() {
        return !builders.isEmpty();
    }

    /**
     * @hidden
     */
    @Override
    public ContentBlock buildAggregated() {
        List<ToolUseBlock> toolCalls = buildAllToolCalls();

        // 若只有一个工具调用,直接返回
        // 若有多个,返回最后一个(也可返回专门的 multi-call 块)
        if (toolCalls.isEmpty()) {
            return null;
        }

        return toolCalls.get(toolCalls.size() - 1);
    }

    /**
     * 构建所有已累积的工具调用。
     *
     * @hidden
     * @return 工具调用列表
     */
    public List<ToolUseBlock> buildAllToolCalls() {
        return builders.values().stream().map(ToolCallBuilder::build).collect(Collectors.toList());
    }

    /**
     * 按 ID 获取已累积的工具调用。
     *
     * <p>如果 ID 为空或未找到对应 builder,该方法会回退到使用 {@code lastToolCallKey}。
     *
     * @param id 要查找的工具调用 ID
     * @return 已累积的 {@link ToolUseBlock},未找到时为 null
     */
    public ToolUseBlock getAccumulatedToolCall(String id) {
        if (id != null && !id.isEmpty()) {
            // 先按 ID 直接查找
            ToolCallBuilder builder = builders.get(id);
            if (builder != null) {
                return builder.build();
            }
        }

        // 回退到 lastToolCallKey(ID 为空或未找到时)
        if (lastToolCallKey != null) {
            ToolCallBuilder builder = builders.get(lastToolCallKey);
            if (builder != null) {
                return builder.build();
            }
        }

        return null;
    }

    /**
     * 获取所有已累积的工具调用。
     *
     * <p>这是 {@link #buildAllToolCalls()} 的别名,用于 API 一致性。
     *
     * @return 所有已累积的 {@link ToolUseBlock} 列表
     */
    public List<ToolUseBlock> getAllAccumulatedToolCalls() {
        return buildAllToolCalls();
    }

    /**
     * 获取当前(最后)正在累积的工具调用 ID。
     *
     * <p>这对于为分片附加正确的工具调用 ID 非常有用,
     * 使用户能正确拼接流式分片。
     *
     * @return 当前工具调用 ID,若没有正在累积的工具调用则为 null
     */
    public String getCurrentToolCallId() {
        if (lastToolCallKey == null) {
            return null;
        }

        ToolCallBuilder builder = builders.get(lastToolCallKey);
        if (builder == null) {
            return null;
        }

        return builder.toolId;
    }

    /**
     * @hidden
     */
    @Override
    public void reset() {
        builders.clear();
        nextIndex = 0;
        lastToolCallKey = null;
    }
}
