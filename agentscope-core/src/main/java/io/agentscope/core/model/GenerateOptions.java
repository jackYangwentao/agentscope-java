/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.core.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 不可变的 LLM 模型生成选项。
 * 使用构建器模式构造实例。
 *
 * <p>该类同时包含每次请求的生成参数（temperature、maxTokens 等）和连接级别配置
 * （apiKey、baseUrl、modelName、stream）。
 */
public class GenerateOptions {
    // ==================== 连接级别配置 ====================

    /** 用于 LLM 提供商身份认证的 API 密钥。为 null 时使用 Model 级别的默认密钥。 */
    private final String apiKey;

    /** LLM 提供商 API 的基础 URL（如 "https://api.openai.com"）。为 null 时使用 Model 默认值。 */
    private final String baseUrl;

    /** API 请求的端点路径（如 "/v1/chat/completions"）。支持兼容 OpenAI 接口的自定义端点。 */
    private final String endpointPath;

    /** 用于生成响应的模型名称（如 "gpt-4"、"qwen-plus"）。为 null 时使用 Model 默认值。 */
    private final String modelName;

    /** 是否启用流式输出。为 true 时逐 token 返回，为 false 时等待完整响应后返回。为 null 时使用 Model 默认值。 */
    private final Boolean stream;

    // ==================== 生成参数 ====================

    /** 温度参数，控制输出的随机性。值越高（如 0.8）输出越随机，值越低（如 0.2）输出越确定。范围 0-2。 */
    private final Double temperature;

    /** Top-P（核采样）参数。考虑累积概率超过该值的最小 token 集合，控制生成多样性。范围 0-1。 */
    private final Double topP;

    /** 生成响应的最大 token 数上限。 */
    private final Integer maxTokens;

    /** 补全 token 的最大数量限制（OpenAI 兼容接口的 max_completion_tokens 字段）。与 maxTokens 互斥或共存取决于提供商。 */
    private final Integer maxCompletionTokens;

    /** 频率惩罚系数。根据 token 在已生成文本中出现频率进行惩罚，值越高重复越少。范围 -2 到 2。 */
    private final Double frequencyPenalty;

    /** 存在惩罚系数。对已出现在文本中的 token 进行惩罚，值越高越倾向于讨论新主题。范围 -2 到 2。 */
    private final Double presencePenalty;

    /** 思考预算（Thinking Budget），用于支持思考模式的模型（如 DashScope）。指定推理过程的最大 token 数。 */
    private final Integer thinkingBudget;

    /** 推理努力级别（Reasoning Effort），用于 o1 系列模型。可选值："low"、"medium"、"high"。 */
    private final String reasoningEffort;

    /** 模型调用的执行配置（超时、重试策略和错误过滤）。为 null 时不进行特殊配置。 */
    private final ExecutionConfig executionConfig;

    /** 工具选择策略，控制模型如何使用工具。可选：auto（自动决策）/ none（禁止调用）/ required（强制调用）/ specific（指定工具）。 */
    private final ToolChoice toolChoice;

    /** Top-K 采样参数。限制模型每一步只考虑概率最高的 K 个 token。值越小输出越聚焦。 */
    private final Integer topK;

    /** 随机种子，用于确定性生成。设置后相同输入和种子可复现相同的输出结果。 */
    private final Long seed;

    /** 是否启用提示缓存（Prompt Caching）。启用后，Formatter 会自动为 system 消息和最后一条消息添加 cache_control 标记。 */
    private final Boolean cacheControl;

    /** 是否启用并行工具调用。启用后模型可同时调用多个工具。 */
    private final Boolean parallelToolCalls;

    /** 附加 HTTP 请求头，会在 API 调用时合并到默认请求头中。用于自定义认证、链路追踪或提供商特定参数。 */
    private final Map<String, String> additionalHeaders;

    /** 附加请求体参数，会合并到 API 请求体中，用于传递标准字段未覆盖的提供商特定选项。 */
    private final Map<String, Object> additionalBodyParams;

    /** 附加 URL 查询参数，会追加到 API 请求 URL 的查询字符串中。 */
    private final Map<String, String> additionalQueryParams;

    /**
     * Creates a new GenerateOptions instance using the builder pattern.
     *
     * @param builder the builder containing the generation options configuration
     */
    private GenerateOptions(Builder builder) {
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.endpointPath = builder.endpointPath;
        this.modelName = builder.modelName;
        this.stream = builder.stream;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxTokens = builder.maxTokens;
        this.maxCompletionTokens = builder.maxCompletionTokens;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
        this.thinkingBudget = builder.thinkingBudget;
        this.reasoningEffort = builder.reasoningEffort;
        this.executionConfig = builder.executionConfig;
        this.toolChoice = builder.toolChoice;
        this.topK = builder.topK;
        this.seed = builder.seed;
        this.cacheControl = builder.cacheControl;
        this.parallelToolCalls = builder.parallelToolCalls;
        this.additionalHeaders =
                builder.additionalHeaders != null
                        ? Collections.unmodifiableMap(new HashMap<>(builder.additionalHeaders))
                        : Collections.emptyMap();
        this.additionalBodyParams =
                builder.additionalBodyParams != null
                        ? Collections.unmodifiableMap(new HashMap<>(builder.additionalBodyParams))
                        : Collections.emptyMap();
        this.additionalQueryParams =
                builder.additionalQueryParams != null
                        ? Collections.unmodifiableMap(new HashMap<>(builder.additionalQueryParams))
                        : Collections.emptyMap();
    }

    /**
     * Gets the API key for authentication.
     *
     * <p>This is the API key used to authenticate with the LLM provider.
     * When null, the model's default API key will be used (if configured).
     *
     * @return the API key, or null if not set
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Gets the base URL for the API endpoint.
     *
     * <p>This is the base URL of the LLM provider's API.
     * When null, the model's default base URL will be used (if configured).
     *
     * @return the base URL, or null if not set
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Gets the endpoint path for the API request.
     *
     * <p>This is the API endpoint path (e.g., "/v1/chat/completions").
     * When null, the model's default endpoint path will be used.
     *
     * <p>This allows customization for OpenAI-compatible APIs that use different
     * endpoint paths than the standard OpenAI API.
     *
     * @return the endpoint path, or null if not set
     */
    public String getEndpointPath() {
        return endpointPath;
    }

    /**
     * Gets the model name to use for generation.
     *
     * <p>This specifies which model to use (e.g., "gpt-4", "gpt-3.5-turbo").
     * When null, the model's default model name will be used (if configured).
     *
     * @return the model name, or null if not set
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Gets whether streaming mode is enabled.
     *
     * <p>When true, responses will be streamed as they are generated.
     * When false, the full response will be returned when complete.
     * When null, the model's default streaming mode will be used (if configured).
     *
     * @return true for streaming, false for non-streaming, null if not set
     */
    public Boolean getStream() {
        return stream;
    }

    /**
     * Gets the temperature for text generation.
     *
     * <p>Higher values (e.g., 0.8) make output more random, while lower values
     * (e.g., 0.2) make it more focused and deterministic.
     *
     * @return the temperature value between 0 and 2, or null if not set
     */
    public Double getTemperature() {
        return temperature;
    }

    /**
     * Gets the top-p (nucleus sampling) parameter.
     *
     * <p>Controls diversity via nucleus sampling: considers the smallest set of tokens
     * whose cumulative probability exceeds the top_p value.
     *
     * @return the top-p value between 0 and 1, or null if not set
     */
    public Double getTopP() {
        return topP;
    }

    /**
     * Gets the maximum number of tokens to generate.
     *
     * @return the maximum tokens limit, or null if not set
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Gets the maximum number of completion tokens to generate.
     *
     * <p>This is an alternative to {@link #getMaxTokens()} for OpenAI-compatible APIs that support
     * {@code max_completion_tokens}. Some providers/models treat {@code max_tokens} and
     * {@code max_completion_tokens} as mutually exclusive; this SDK does not enforce exclusivity
     * and will forward exactly what the caller sets.
     *
     * @return the maximum completion tokens limit, or null if not set
     */
    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    /**
     * Gets the frequency penalty.
     *
     * <p>Reduces repetition by penalizing tokens based on their frequency in the text so far.
     * Higher values decrease repetition more strongly.
     *
     * @return the frequency penalty between -2 and 2, or null if not set
     */
    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    /**
     * Gets the presence penalty.
     *
     * <p>Reduces repetition by penalizing tokens that have already appeared in the text.
     * Higher values decrease repetition more strongly.
     *
     * @return the presence penalty between -2 and 2, or null if not set
     */
    public Double getPresencePenalty() {
        return presencePenalty;
    }

    /**
     * Gets the maximum number of tokens for reasoning/thinking content.
     *
     * <p>This parameter is specific to models that support thinking mode (e.g., DashScope).
     * When set, it enables the model to show its reasoning process before generating the final
     * answer.
     *
     * @return the thinking budget in tokens, or null if not set
     */
    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    /**
     * Gets the reasoning effort level for o1 models.
     *
     * <p>This parameter controls how much effort the model spends on reasoning.
     * Valid values are "low", "medium", and "high".
     *
     * @return the reasoning effort level, or null if not set
     */
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * Gets the execution configuration for timeout and retry behavior.
     *
     * <p>When set, the model will apply timeout and retry logic according to the
     * configured execution config (timeout duration, max attempts, backoff, error filtering).
     *
     * @return the execution configuration, or null if not configured
     */
    public ExecutionConfig getExecutionConfig() {
        return executionConfig;
    }

    /**
     * Gets the tool choice configuration for controlling how the model uses tools.
     *
     * <p>When set, this controls whether the model can call tools, must call tools,
     * or must call a specific tool. When null, the default behavior (auto) is used.
     *
     * @return the tool choice configuration, or null if not set (defaults to auto)
     * @see ToolChoice
     */
    public ToolChoice getToolChoice() {
        return toolChoice;
    }

    /**
     * Gets the top-k sampling parameter.
     *
     * <p>Limits the model to only consider the top K most probable tokens at each step.
     * Lower values make output more focused, higher values allow more diversity.
     *
     * @return the top-k value, or null if not set
     */
    public Integer getTopK() {
        return topK;
    }

    /**
     * Gets the random seed for deterministic generation.
     *
     * <p>When set, the model will attempt to generate the same output for the same
     * input and seed value, enabling reproducible results.
     *
     * @return the seed value, or null if not set
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Gets whether cache control is enabled for prompt caching.
     *
     * <p>When true, the formatter will automatically add <code>cache_control:
     * {"type": "ephemeral"}</code> to system messages and the last message in the request. This
     * enables prompt
     * caching on supported providers (e.g., Anthropic, DashScope, OpenAI-compatible APIs) to reduce
     * latency and cost.
     *
     * <p>Users can also manually mark individual messages for caching via {@link
     * io.agentscope.core.message.MessageMetadataKeys#CACHE_CONTROL} metadata. Manually marked
     * messages take priority over the automatic strategy.
     *
     * @return true if cache control is enabled, false or null if not set
     */
    public Boolean getCacheControl() {
        return cacheControl;
    }

    /**
     * Gets whether parallel tool calls are enabled.
     *
     * <p>When true, enable parallel function calling during tool use.
     *
     * @return true if parallel tool calls are enabled, false or null if not set
     */
    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    /**
     * Gets the additional HTTP headers to include in API requests.
     *
     * <p>These headers will be merged with the default headers when making API calls.
     * Useful for passing custom authentication, tracing, or provider-specific headers.
     *
     * @return an unmodifiable map of additional headers, empty if none set
     */
    public Map<String, String> getAdditionalHeaders() {
        return additionalHeaders;
    }

    /**
     * Gets the additional parameters to include in the request body.
     *
     * <p>These parameters will be merged into the API request body, allowing
     * provider-specific options not covered by the standard fields.
     *
     * @return an unmodifiable map of additional body parameters, empty if none set
     */
    public Map<String, Object> getAdditionalBodyParams() {
        return additionalBodyParams;
    }

    /**
     * Gets the additional query parameters to include in API requests.
     *
     * <p>These parameters will be appended to the API request URL as query string.
     *
     * @return an unmodifiable map of additional query parameters, empty if none set
     */
    public Map<String, String> getAdditionalQueryParams() {
        return additionalQueryParams;
    }

    /**
     * Creates a new builder for GenerateOptions.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Merges two GenerateOptions instances, with primary options taking precedence.
     *
     * <p>This method performs parameter-by-parameter merging: for each parameter, if the primary
     * value is non-null, it is used; otherwise, the fallback value is used. This allows proper
     * layering of options from different sources (e.g., per-request options over default options).
     *
     * <p><b>Merge Behavior:</b>
     * <ul>
     *   <li>Primitive fields (temperature, topP, etc.): primary != null ? primary : fallback</li>
     *   <li>Map fields (additionalHeaders, etc.): merges both maps, with primary values overriding fallback</li>
     *   <li>If primary is null, returns fallback directly</li>
     *   <li>If fallback is null, returns primary directly</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * ExecutionConfig defaultExecConfig = ExecutionConfig.builder()
     *     .timeout(Duration.ofMinutes(5))
     *     .maxAttempts(3)
     *     .build();
     *
     * GenerateOptions defaults = GenerateOptions.builder()
     *     .temperature(0.7)
     *     .executionConfig(defaultExecConfig)
     *     .build();
     *
     * ExecutionConfig customExecConfig = ExecutionConfig.builder()
     *     .timeout(Duration.ofSeconds(30))
     *     .build();
     *
     * GenerateOptions perRequest = GenerateOptions.builder()
     *     .executionConfig(customExecConfig)
     *     .build();
     *
     * // Result: temperature=0.7, executionConfig with timeout=30s and maxAttempts=3
     * GenerateOptions merged = GenerateOptions.mergeOptions(perRequest, defaults);
     * }</pre>
     *
     * @param primary the primary options (higher priority)
     * @param fallback the fallback options (lower priority)
     * @return merged options, or null if both are null
     */
    public static GenerateOptions mergeOptions(GenerateOptions primary, GenerateOptions fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }

        Builder builder = builder();
        builder.apiKey(primary.apiKey != null ? primary.apiKey : fallback.apiKey);
        builder.baseUrl(primary.baseUrl != null ? primary.baseUrl : fallback.baseUrl);
        builder.endpointPath(
                primary.endpointPath != null ? primary.endpointPath : fallback.endpointPath);
        builder.modelName(primary.modelName != null ? primary.modelName : fallback.modelName);
        builder.stream(primary.stream != null ? primary.stream : fallback.stream);
        builder.temperature(
                primary.temperature != null ? primary.temperature : fallback.temperature);
        builder.topP(primary.topP != null ? primary.topP : fallback.topP);
        builder.maxTokens(primary.maxTokens != null ? primary.maxTokens : fallback.maxTokens);
        builder.maxCompletionTokens(
                primary.maxCompletionTokens != null
                        ? primary.maxCompletionTokens
                        : fallback.maxCompletionTokens);
        builder.frequencyPenalty(
                primary.frequencyPenalty != null
                        ? primary.frequencyPenalty
                        : fallback.frequencyPenalty);
        builder.presencePenalty(
                primary.presencePenalty != null
                        ? primary.presencePenalty
                        : fallback.presencePenalty);
        builder.thinkingBudget(
                primary.thinkingBudget != null ? primary.thinkingBudget : fallback.thinkingBudget);
        builder.reasoningEffort(
                primary.reasoningEffort != null
                        ? primary.reasoningEffort
                        : fallback.reasoningEffort);
        builder.executionConfig(
                ExecutionConfig.mergeConfigs(primary.executionConfig, fallback.executionConfig));
        builder.toolChoice(primary.toolChoice != null ? primary.toolChoice : fallback.toolChoice);
        builder.topK(primary.topK != null ? primary.topK : fallback.topK);
        builder.seed(primary.seed != null ? primary.seed : fallback.seed);
        builder.cacheControl(
                primary.cacheControl != null ? primary.cacheControl : fallback.cacheControl);
        builder.parallelToolCalls(
                primary.parallelToolCalls != null
                        ? primary.parallelToolCalls
                        : fallback.parallelToolCalls);

        // Merge map fields: fallback first, then override with primary
        mergeMaps(fallback.additionalHeaders, primary.additionalHeaders, builder::additionalHeader);
        mergeMaps(
                fallback.additionalBodyParams,
                primary.additionalBodyParams,
                builder::additionalBodyParam);
        mergeMaps(
                fallback.additionalQueryParams,
                primary.additionalQueryParams,
                builder::additionalQueryParam);

        return builder.build();
    }

    private static <V> void mergeMaps(
            Map<String, V> fallback, Map<String, V> primary, BiConsumer<String, V> adder) {
        if (fallback != null && !fallback.isEmpty()) {
            for (Map.Entry<String, V> entry : fallback.entrySet()) {
                adder.accept(entry.getKey(), entry.getValue());
            }
        }
        if (primary != null && !primary.isEmpty()) {
            for (Map.Entry<String, V> entry : primary.entrySet()) {
                adder.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    public static class Builder {
        // ==================== 连接级别配置 ====================

        /** API 密钥。 */
        private String apiKey;

        /** API 基础 URL。 */
        private String baseUrl;

        /** API 请求端点路径。 */
        private String endpointPath;

        /** 模型名称。 */
        private String modelName;

        /** 是否启用流式输出。 */
        private Boolean stream;

        // ==================== 生成参数 ====================

        /** 温度参数 (0-2)。 */
        private Double temperature;

        /** Top-P 核采样参数 (0-1)。 */
        private Double topP;

        /** 最大生成 token 数。 */
        private Integer maxTokens;

        /** 最大补全 token 数（OpenAI 兼容接口）。 */
        private Integer maxCompletionTokens;

        /** 频率惩罚系数 (-2 到 2)。 */
        private Double frequencyPenalty;

        /** 存在惩罚系数 (-2 到 2)。 */
        private Double presencePenalty;

        /** 思考预算（推理过程 token 数）。 */
        private Integer thinkingBudget;

        /** 推理努力级别（low/medium/high）。 */
        private String reasoningEffort;

        /** 模型调用的执行配置（超时、重试）。 */
        private ExecutionConfig executionConfig;

        /** 工具选择策略。 */
        private ToolChoice toolChoice;

        /** Top-K 采样参数。 */
        private Integer topK;

        /** 随机种子（确定性生成）。 */
        private Long seed;

        /** 是否启用提示缓存。 */
        private Boolean cacheControl;

        /** 是否启用并行工具调用。 */
        private Boolean parallelToolCalls;

        /** 附加 HTTP 请求头。 */
        private Map<String, String> additionalHeaders;

        /** 附加请求体参数。 */
        private Map<String, Object> additionalBodyParams;

        /** 附加 URL 查询参数。 */
        private Map<String, String> additionalQueryParams;

        /**
         * Sets the API key for authentication.
         *
         * @param apiKey the API key
         * @return this builder instance
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the base URL for the API endpoint.
         *
         * @param baseUrl the base URL
         * @return this builder instance
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the endpoint path for the API request.
         *
         * <p>This allows customization for OpenAI-compatible APIs that use different
         * endpoint paths than the standard OpenAI API (e.g., "/v4/chat/completions",
         * "/api/v1/llm/chat", etc.). When null, the default endpoint path will be used.
         *
         * @param endpointPath the endpoint path (e.g., "/v1/chat/completions")
         * @return this builder instance
         */
        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }

        /**
         * Sets the model name to use for generation.
         *
         * @param modelName the model name (e.g., "gpt-4", "gpt-3.5-turbo")
         * @return this builder instance
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets whether streaming mode is enabled.
         *
         * @param stream true for streaming, false for non-streaming
         * @return this builder instance
         */
        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * Sets the temperature for text generation.
         *
         * <p>Higher values (e.g., 0.8) make output more random, while lower values
         * (e.g., 0.2) make it more focused and deterministic.
         *
         * @param temperature the temperature value between 0 and 2
         * @return this builder instance
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the top-p (nucleus sampling) parameter.
         *
         * <p>Controls diversity via nucleus sampling: considers the smallest set of tokens
         * whose cumulative probability exceeds the top_p value.
         *
         * @param topP the top-p value between 0 and 1
         * @return this builder instance
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate.
         *
         * @param maxTokens the maximum tokens limit
         * @return this builder instance
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the maximum number of completion tokens to generate.
         *
         * <p>This is an alternative to {@link #maxTokens(Integer)} for OpenAI-compatible APIs that
         * support {@code max_completion_tokens}. This builder does not enforce exclusivity with
         * {@code maxTokens}; both may be set and will be forwarded as-is by formatters that support
         * both fields.
         *
         * @param maxCompletionTokens the maximum completion tokens limit
         * @return this builder instance
         */
        public Builder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        /**
         * Sets the frequency penalty.
         *
         * <p>Reduces repetition by penalizing tokens based on their frequency in the text so far.
         * Higher values decrease repetition more strongly.
         *
         * @param frequencyPenalty the frequency penalty between -2 and 2
         * @return this builder instance
         */
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        /**
         * Sets the presence penalty.
         *
         * <p>Reduces repetition by penalizing tokens that have already appeared in the text.
         * Higher values decrease repetition more strongly.
         *
         * @param presencePenalty the presence penalty between -2 and 2
         * @return this builder instance
         */
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        /**
         * Sets the thinking budget (maximum tokens for reasoning/thinking content).
         *
         * <p>This parameter is specific to models that support thinking mode. When set, the model
         * will show its reasoning process before generating the final answer. Setting this
         * parameter may automatically enable thinking mode in some models.
         *
         * @param thinkingBudget the maximum tokens for thinking content
         * @return this builder
         */
        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        /**
         * Sets the reasoning effort level for o1 models.
         *
         * <p>This parameter controls how much effort the model spends on reasoning.
         * Valid values are "low", "medium", and "high".
         *
         * @param reasoningEffort the reasoning effort level
         * @return this builder
         */
        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /**
         * Sets the execution configuration for timeout and retry behavior.
         *
         * <p>When configured, model API calls will apply timeout and retry logic according
         * to the execution config (timeout duration, max attempts, backoff, error filtering).
         *
         * @param executionConfig the execution configuration, or null to disable
         * @return this builder instance
         */
        public Builder executionConfig(ExecutionConfig executionConfig) {
            this.executionConfig = executionConfig;
            return this;
        }

        /**
         * Sets the tool choice configuration for controlling how the model uses tools.
         *
         * <p>This setting controls whether the model can call tools, must call tools,
         * or must call a specific tool:
         * <ul>
         *   <li>{@link ToolChoice.Auto} - Let model decide (default when null)</li>
         *   <li>{@link ToolChoice.None} - Prevent tool calling</li>
         *   <li>{@link ToolChoice.Required} - Force at least one tool call</li>
         *   <li>{@link ToolChoice.Specific} - Force specific tool call</li>
         * </ul>
         *
         * @param toolChoice the tool choice configuration, or null for default (auto)
         * @return this builder instance
         * @see ToolChoice
         */
        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        /**
         * Sets the top-k sampling parameter.
         *
         * <p>Limits the model to only consider the top K most probable tokens at each step.
         * Lower values make output more focused, higher values allow more diversity.
         *
         * @param topK the top-k value
         * @return this builder instance
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Sets the random seed for deterministic generation.
         *
         * <p>When set, the model will attempt to generate the same output for the same
         * input and seed value, enabling reproducible results.
         *
         * @param seed the seed value
         * @return this builder instance
         */
        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets whether cache control is enabled for prompt caching.
         *
         * <p>When true, the formatter will automatically add <code>cache_control:
         * {"type": "ephemeral"}</code> to system messages and the last message in the request.
         *
         * @param cacheControl true to enable cache control, false to disable
         * @return this builder instance
         */
        public Builder cacheControl(Boolean cacheControl) {
            this.cacheControl = cacheControl;
            return this;
        }

        /**
         * Sets whether to enable parallel function calling during tool use.
         *
         * @param parallelToolCalls true to enable parallel tool calls, false to disable
         * @return this builder instance
         */
        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        /**
         * Adds an additional HTTP header to include in API requests.
         *
         * @param key the header name
         * @param value the header value
         * @return this builder instance
         */
        public Builder additionalHeader(String key, String value) {
            if (this.additionalHeaders == null) {
                this.additionalHeaders = new HashMap<>();
            }
            this.additionalHeaders.put(key, value);
            return this;
        }

        /**
         * Sets all additional HTTP headers to include in API requests.
         *
         * @param headers the headers map
         * @return this builder instance
         */
        public Builder additionalHeaders(Map<String, String> headers) {
            this.additionalHeaders = headers != null ? new HashMap<>(headers) : null;
            return this;
        }

        /**
         * Adds an additional parameter to include in the request body.
         *
         * @param key the parameter name
         * @param value the parameter value
         * @return this builder instance
         */
        public Builder additionalBodyParam(String key, Object value) {
            if (this.additionalBodyParams == null) {
                this.additionalBodyParams = new HashMap<>();
            }
            this.additionalBodyParams.put(key, value);
            return this;
        }

        /**
         * Sets all additional parameters to include in the request body.
         *
         * @param params the parameters map
         * @return this builder instance
         */
        public Builder additionalBodyParams(Map<String, Object> params) {
            this.additionalBodyParams = params != null ? new HashMap<>(params) : null;
            return this;
        }

        /**
         * Adds an additional query parameter to include in API requests.
         *
         * @param key the parameter name
         * @param value the parameter value
         * @return this builder instance
         */
        public Builder additionalQueryParam(String key, String value) {
            if (this.additionalQueryParams == null) {
                this.additionalQueryParams = new HashMap<>();
            }
            this.additionalQueryParams.put(key, value);
            return this;
        }

        /**
         * Sets all additional query parameters to include in API requests.
         *
         * @param params the parameters map
         * @return this builder instance
         */
        public Builder additionalQueryParams(Map<String, String> params) {
            this.additionalQueryParams = params != null ? new HashMap<>(params) : null;
            return this;
        }

        /**
         * Builds a new GenerateOptions instance with the set values.
         *
         * @return a new GenerateOptions instance
         */
        public GenerateOptions build() {
            return new GenerateOptions(this);
        }
    }
}
