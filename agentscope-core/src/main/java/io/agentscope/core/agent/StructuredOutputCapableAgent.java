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

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.util.JsonSchemaUtils;
import io.agentscope.core.util.JsonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 支持结构化输出生成的 Agent 抽象基类。
 *
 * <p>本类提供使用 {@code generate_response} 工具模式配合 StructuredOutputHook
 * 进行流控制来生成结构化输出的基础设施。
 *
 * <p><b>主要特性:</b>
 * <ul>
 *   <li>自动注册结构化输出工具</li>
 *   <li>工具执行前的 Schema 校验</li>
 *   <li>结构化输出完成后的 memory 压缩</li>
 *   <li>可配置的提醒模式(TOOL_CHOICE 或 PROMPT)</li>
 * </ul>
 *
 * <p><b>子类要求:</b>
 * <ul>
 *   <li>通过构造器提供 Toolkit</li>
 *   <li>实现 {@link #getMemory()} 提供 memory 访问</li>
 *   <li>实现 {@link #buildGenerateOptions()} 提供模型选项</li>
 * </ul>
 *
 * <p>Abstract base class for agents that support structured output generation.
 *
 * <p>This class provides the infrastructure for generating structured output using the
 * {@code generate_response} tool pattern combined with StructuredOutputHook for flow control.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Automatic tool registration for structured output</li>
 *   <li>Schema validation before tool execution</li>
 *   <li>Memory compression after structured output completion</li>
 *   <li>Configurable reminder mode (TOOL_CHOICE or PROMPT)</li>
 * </ul>
 *
 * <p><b>Subclass Requirements:</b>
 * <ul>
 *   <li>Provide Toolkit via constructor</li>
 *   <li>Implement {@link #getMemory()} for memory access</li>
 *   <li>Implement {@link #buildGenerateOptions()} for model options</li>
 * </ul>
 */
public abstract class StructuredOutputCapableAgent extends AgentBase {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputCapableAgent.class);

    /** 结构化输出生成的工具名称。The tool name for structured output generation. */
    public static final String STRUCTURED_OUTPUT_TOOL_NAME = "generate_response";

    protected final Toolkit toolkit;
    protected final StructuredOutputReminder structuredOutputReminder;

    /**
     * 使用默认提醒模式(TOOL_CHOICE)的构造器。
     *
     * @param name Agent 名称
     * @param description Agent 描述
     * @param checkRunning 是否检查运行状态
     * @param hooks Hook 列表
     * @param toolkit 工具包实例
     *
     * <p>Constructor with default reminder mode (TOOL_CHOICE).
     */
    protected StructuredOutputCapableAgent(
            String name,
            String description,
            boolean checkRunning,
            List<Hook> hooks,
            Toolkit toolkit) {
        this(name, description, checkRunning, hooks, toolkit, StructuredOutputReminder.TOOL_CHOICE);
    }

    /**
     * 使用自定义提醒模式的完整构造器。
     *
     * @param name Agent 名称
     * @param description Agent 描述
     * @param checkRunning 是否检查运行状态
     * @param hooks Hook 列表
     * @param toolkit 工具包实例
     * @param structuredOutputReminder 结构化输出提醒模式
     *
     * <p>Constructor with custom reminder mode.
     */
    protected StructuredOutputCapableAgent(
            String name,
            String description,
            boolean checkRunning,
            List<Hook> hooks,
            Toolkit toolkit,
            StructuredOutputReminder structuredOutputReminder) {
        super(name, description, checkRunning, hooks);
        this.toolkit = toolkit != null ? toolkit : new Toolkit();
        this.structuredOutputReminder =
                structuredOutputReminder != null
                        ? structuredOutputReminder
                        : StructuredOutputReminder.TOOL_CHOICE;
    }

    /**
     * 获取工具包实例。
     *
     * @return 当前 Agent 关联的 Toolkit
     *
     * <p>Get the toolkit for tool operations.
     */
    public Toolkit getToolkit() {
        return toolkit;
    }

    /**
     * 获取 memory 实例,供结构化输出 Hook 使用。
     * 子类必须实现此方法。
     *
     * @return Memory 实例
     *
     * <p>Get the memory for structured output hook.
     * Subclasses must implement this.
     */
    public abstract Memory getMemory();

    /**
     * 构建模型调用所需的生成选项。
     * 子类必须实现此方法。
     *
     * @return GenerateOptions 实例
     *
     * <p>Build generate options for model calls.
     * Subclasses must implement this.
     */
    protected abstract GenerateOptions buildGenerateOptions();

    // ==================== Structured Output Implementation ====================

    @Override
    protected final Mono<Msg> doCall(List<Msg> msgs, Class<?> structuredOutputClass) {
        return executeWithStructuredOutput(msgs, structuredOutputClass, null);
    }

    @Override
    protected final Mono<Msg> doCall(List<Msg> msgs, JsonNode outputSchema) {
        return executeWithStructuredOutput(msgs, null, outputSchema);
    }

    /**
     * 使用 StructuredOutputHook 执行结构化输出。
     *
     * <p>Execute with structured output using StructuredOutputHook.
     */
    private Mono<Msg> executeWithStructuredOutput(
            List<Msg> msgs, Class<?> targetClass, JsonNode schemaDesc) {

        // 校验参数
        // Validate parameters
        if (targetClass == null && schemaDesc == null) {
            return Mono.error(
                    new IllegalArgumentException(
                            "Either targetClass or schemaDesc must be provided"));
        }
        if (targetClass != null && schemaDesc != null) {
            return Mono.error(
                    new IllegalArgumentException("Cannot provide both targetClass and schemaDesc"));
        }

        return Mono.defer(
                () -> {
                    // 创建并注册临时工具
                    // Create and register temporary tool
                    Map<String, Object> jsonSchema =
                            targetClass != null
                                    ? JsonSchemaUtils.generateSchemaFromClass(targetClass)
                                    : JsonSchemaUtils.generateSchemaFromJsonNode(schemaDesc);
                    AgentTool structuredOutputTool =
                            createStructuredOutputTool(jsonSchema, targetClass, schemaDesc);
                    toolkit.registerAgentTool(structuredOutputTool);

                    // 创建流控制 Hook
                    // Create hook for flow control
                    StructuredOutputHook hook =
                            new StructuredOutputHook(
                                    structuredOutputReminder, buildGenerateOptions(), getMemory());

                    addHook(hook);

                    return doCall(msgs)
                            .flatMap(
                                    result -> {
                                        // 从 Hook 的输出中提取结果
                                        // Extract result from hook's output
                                        Msg hookResult = hook.getResultMsg();
                                        if (hookResult != null) {
                                            Msg extracted = extractStructuredResult(hookResult);
                                            // 合并来自 reasoning 轮的聚合元数据
                                            // Merge aggregated metadata from reasoning rounds
                                            if (extracted != null) {
                                                extracted =
                                                        mergeCollectedMetadata(
                                                                extracted,
                                                                hook.getAggregatedUsage(),
                                                                hook.getAggregatedThinking());
                                            }
                                            return Mono.just(extracted);
                                        }
                                        return Mono.just(result);
                                    })
                            .doFinally(
                                    signal -> {
                                        removeHook(hook);
                                        toolkit.removeToolIfSame(
                                                STRUCTURED_OUTPUT_TOOL_NAME,
                                                structuredOutputTool);
                                    });
                });
    }

    /**
     * 创建带校验的结构化输出工具。
     *
     * <p>Create the structured output tool with validation.
     */
    private AgentTool createStructuredOutputTool(
            Map<String, Object> schema, Class<?> targetClass, JsonNode schemaDesc) {
        return new AgentTool() {
            @Override
            public String getName() {
                return STRUCTURED_OUTPUT_TOOL_NAME;
            }

            @Override
            public String getDescription() {
                return "Generate the final structured response. Call this function when"
                        + " you have all the information needed to provide a complete answer.";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> params = new HashMap<>();
                params.put("type", "object");

                // 浅拷贝内部 schema,以便安全地将 $defs 提升到外层 params 根节点,
                // 避免修改共享的 schema 实例。
                // Shallow-copy the inner schema so we can safely hoist $defs to the
                // outer params root without mutating the shared `schema` instance.
                Map<String, Object> innerSchema = new HashMap<>(schema);
                Map<String, Object> hoistedDefs = new HashMap<>();
                hoistDefsKey(innerSchema, "$defs", hoistedDefs);
                hoistDefsKey(innerSchema, "definitions", hoistedDefs);

                params.put("properties", Map.of("response", innerSchema));
                params.put("required", List.of("response"));
                if (!hoistedDefs.isEmpty()) {
                    params.put("$defs", hoistedDefs);
                }
                return params;
            }

            @Override
            public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
                return Mono.fromCallable(
                        () -> {
                            Object responseData = param.getInput().get("response");

                            // 工具只负责存储原始数据,校验由 ToolExecutor 在调用前完成
                            // The tool simply stores the raw data
                            // Validation is done by ToolExecutor before calling this
                            String contentText = "";
                            if (responseData != null) {
                                try {
                                    contentText = JsonUtils.getJsonCodec().toJson(responseData);
                                } catch (Exception e) {
                                    contentText = responseData.toString();
                                }
                            }

                            log.debug("Structured output generated: {}", contentText);

                            // 创建响应消息
                            // Create response message
                            Msg responseMsg =
                                    Msg.builder()
                                            .name(getName())
                                            .role(MsgRole.ASSISTANT)
                                            .content(
                                                    TextBlock.builder()
                                                            .text(contentText)
                                                            .build())
                                            .metadata(
                                                    responseData != null
                                                            ? Map.of("response", responseData)
                                                            : Map.of())
                                            .build();

                            Map<String, Object> toolMetadata = new HashMap<>();
                            toolMetadata.put("success", true);
                            toolMetadata.put("response_msg", responseMsg);

                            return ToolResultBlock.of(
                                    List.of(
                                            TextBlock.builder()
                                                    .text("Successfully generated response.")
                                                    .build()),
                                    toolMetadata);
                        });
            }
        };
    }

    /**
     * 从工具结果消息中提取结构化输出。
     *
     * <p>Extract structured result from tool result message.
     */
    private Msg extractStructuredResult(Msg hookResultMsg) {
        if (hookResultMsg == null) {
            return null;
        }

        List<ToolResultBlock> toolResults =
                hookResultMsg.getContentBlocks(ToolResultBlock.class);
        for (ToolResultBlock result : toolResults) {
            if (result.getMetadata() != null
                    && Boolean.TRUE.equals(result.getMetadata().get("success"))
                    && result.getMetadata().containsKey("response_msg")) {
                Object responseMsgObj = result.getMetadata().get("response_msg");
                if (responseMsgObj instanceof Msg responseMsg) {
                    return extractResponseData(responseMsg);
                }
            }
        }

        return hookResultMsg;
    }

    @SuppressWarnings("unchecked")
    private Msg extractResponseData(Msg responseMsg) {
        if (responseMsg.getMetadata() != null
                && responseMsg.getMetadata().containsKey("response")) {
            Object responseData = responseMsg.getMetadata().get("response");
            // 保留所有原始元数据,将结构化输出加入专用 key 下
            // Preserve all original metadata and add structured output under dedicated key
            Map<String, Object> metadata = new HashMap<>(responseMsg.getMetadata());
            metadata.put(MessageMetadataKeys.STRUCTURED_OUTPUT, responseData);
            metadata.remove("response"); // 移除临时 key,使用标准 key
            return Msg.builder()
                    .name(responseMsg.getName())
                    .role(responseMsg.getRole())
                    .content(responseMsg.getContent())
                    .metadata(metadata)
                    .build();
        }
        return responseMsg;
    }

    /**
     * 将收集的元数据(ChatUsage 和 ThinkingBlock)合并到消息中。
     *
     * <p>Merge collected metadata (ChatUsage and ThinkingBlock) into the message.
     */
    private Msg mergeCollectedMetadata(Msg msg, ChatUsage chatUsage, ThinkingBlock thinking) {
        // 合并 ChatUsage 到 metadata
        // Merge ChatUsage into metadata
        Map<String, Object> metadata =
                new HashMap<>(msg.getMetadata() != null ? msg.getMetadata() : Map.of());
        if (chatUsage != null) {
            metadata.put(MessageMetadataKeys.CHAT_USAGE, chatUsage);
        }

        // 合并 ThinkingBlock 到内容
        // Merge ThinkingBlock into content
        List<ContentBlock> newContent;
        if (thinking != null) {
            newContent = new ArrayList<>();
            newContent.add(thinking); // ThinkingBlock 在前
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
     * 将 {@code innerSchema} 下 {@code key} 对应的条目提升到 {@code target} 中。
     *
     * <p>当类级别的 JSON Schema 嵌套在 {@code properties.response} 下时,
     * 其携带的 {@code $defs}/{@code definitions} 会位于
     * {@code properties.response.$defs}。然而像 {@code #/$defs/Foo} 这样的引用
     * 是从文档根节点解析的,因此我们需要将它们提升到外层参数根节点。
     *
     * <p>Move the entries under {@code key} from {@code innerSchema} up into {@code target}.
     *
     * <p>When a class-level JSON Schema is nested under {@code properties.response}, any
     * {@code $defs}/{@code definitions} it carries would end up at
     * {@code properties.response.$defs}. References like {@code #/$defs/Foo} are resolved
     * from the document root though, so we hoist them to the outer parameters root.
     */
    @SuppressWarnings("unchecked")
    private static void hoistDefsKey(
            Map<String, Object> innerSchema, String key, Map<String, Object> target) {
        Object raw = innerSchema.remove(key);
        if (raw instanceof Map<?, ?> defs && !defs.isEmpty()) {
            target.putAll((Map<String, Object>) defs);
        }
    }
}
