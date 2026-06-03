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

import io.agentscope.core.model.exception.BadRequestException;
import io.agentscope.core.model.exception.RateLimitException;
import io.agentscope.core.model.transport.HttpTransportException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * 超时和重试行为的统一执行配置。
 *
 * <p>该类替代了之前的 TimeoutConfig 和 RetryConfig 类，为模型 API 调用和工具执行的
 * 执行行为控制提供单一统一配置。
 *
 * <p>使用构建器模式构造实例。所有字段均为可选的且可为 null。
 *
 * <h2>标准默认值</h2>
 *
 * <ul>
 *   <li>{@link #MODEL_DEFAULTS}：5 分钟超时，3 次指数退避重试
 *   <li>{@link #TOOL_DEFAULTS}：5 分钟超时，无重试（仅 1 次尝试）
 * </ul>
 *
 * <h2>配置合并</h2>
 *
 * <p>使用 {@link #mergeConfigs(ExecutionConfig, ExecutionConfig)} 以按参数优先级组合配置。
 * 这允许从不同来源分层叠加配置：
 *
 * <pre>{@code
 * // 优先级：每次请求 > 智能体级别 > 组件默认值 > 系统默认值
 * ExecutionConfig effective = ExecutionConfig.mergeConfigs(
 *     perRequestConfig,
 *     ExecutionConfig.mergeConfigs(agentConfig, ExecutionConfig.MODEL_DEFAULTS)
 * );
 * }</pre>
 */
public class ExecutionConfig {
    /** 单次执行（模型请求或工具调用）的超时时长。 */
    private final Duration timeout;

    /** 最大尝试次数（包含首次尝试），如 3 表示 1 次初始 + 2 次重试。 */
    private final Integer maxAttempts;

    /** 首次重试的初始退避时长。 */
    private final Duration initialBackoff;

    /** 重试之间的最大退避时长。 */
    private final Duration maxBackoff;

    /** 每次重试后退避时长的乘数（指数退避）。 */
    private final Double backoffMultiplier;

    /** 判定某异常是否应触发重试的断言。 */
    private final Predicate<Throwable> retryOn;

    /**
     * 判定异常是否应触发重试的默认断言。
     *
     * <p>可重试的异常包括：
     * <ul>
     *   <li>HTTP 429（限流）</li>
     *   <li>HTTP 5xx（服务端错误）</li>
     *   <li>超时异常</li>
     *   <li>网络/IO 异常</li>
     * </ul>
     *
     * <p>不可重试的异常包括：
     * <ul>
     *   <li>HTTP 400（参数校验失败）</li>
     *   <li>HTTP 401/403（认证/授权错误）</li>
     *   <li>其他 4xx 客户端错误</li>
     * </ul>
     */
    public static final Predicate<Throwable> RETRYABLE_ERRORS = ExecutionConfig::isRetryableError;

    /**
     * 检查给定异常是否可重试。
     * <p>
     * 规则：BadRequestException 不重试，限流/服务端错误/超时/网络异常可重试，
     * 对于包装异常会递归检查 cause。
     * </p>
     *
     * @param error 待检查的异常
     * @return 如果异常应触发重试则返回 true
     */
    private static boolean isRetryableError(Throwable error) {
        // BadRequestException (400) should not be retried - it's a permanent failure
        if (error instanceof BadRequestException) {
            return false;
        }

        // For HttpTransportException, use the built-in isRetryable() method
        // which returns true for 429 (rate limiting) and 5xx (server errors)
        if (error instanceof HttpTransportException hte) {
            return hte.isRetryable();
        }
        if (error instanceof RateLimitException) {
            return true;
        }

        // Timeout errors are retryable
        if (error instanceof TimeoutException) {
            return true;
        }

        // Network/IO errors are retryable
        if (error instanceof IOException) {
            return true;
        }

        // Check if cause is retryable (for wrapped exceptions)
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            return isRetryableError(cause);
        }

        // Unknown errors - don't retry by default to avoid hiding issues
        return false;
    }

    /**
     * 模型 API 调用的标准默认配置。
     *
     * <ul>
     *   <li>超时：5 分钟</li>
     *   <li>最大尝试次数：3（初始 + 2 次重试）</li>
     *   <li>初始退避：2 秒</li>
     *   <li>最大退避：30 秒</li>
     *   <li>退避乘数：2.0（指数退避）</li>
     *   <li>重试条件：仅可重试异常（429、5xx、超时、网络错误）</li>
     * </ul>
     *
     * <p>注意：退避时间设置较高（初始 2 秒，最大 30 秒），
     * 以更好地应对高并发场景下模型提供商的限流（HTTP 429）。
     */
    public static final ExecutionConfig MODEL_DEFAULTS =
            builder()
                    .timeout(Duration.ofMinutes(5))
                    .maxAttempts(3)
                    .initialBackoff(Duration.ofSeconds(2))
                    .maxBackoff(Duration.ofSeconds(30))
                    .backoffMultiplier(2.0)
                    .retryOn(RETRYABLE_ERRORS)
                    .build();

    /**
     * 工具执行的标准默认配置。
     *
     * <ul>
     *   <li>超时：5 分钟</li>
     *   <li>最大尝试次数：1（不重试）</li>
     * </ul>
     */
    public static final ExecutionConfig TOOL_DEFAULTS =
            builder().timeout(Duration.ofMinutes(5)).maxAttempts(1).build();

    private ExecutionConfig(Builder builder) {
        this.timeout = builder.timeout;
        this.maxAttempts = builder.maxAttempts;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.retryOn = builder.retryOn;
    }

    /**
     * 获取超时时长。
     *
     * @return 超时时长，未设置时返回 null
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 获取最大尝试次数（包含首次尝试）。
     *
     * @return 最大尝试次数，未设置时返回 null
     */
    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * 获取首次重试的初始退避时长。
     *
     * @return 初始退避时长，未设置时返回 null
     */
    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    /**
     * 获取重试之间的最大退避时长。
     *
     * @return 最大退避时长，未设置时返回 null
     */
    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    /**
     * 获取退避乘数。
     *
     * @return 退避乘数，未设置时返回 null
     */
    public Double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * 获取重试判定断言。
     *
     * @return 用于判定异常是否应触发重试的断言，未设置时返回 null
     */
    public Predicate<Throwable> getRetryOn() {
        return retryOn;
    }

    /**
     * 创建 ExecutionConfig 构建器。
     *
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 合并两个 ExecutionConfig 实例，主配置优先级更高。
     *
     * <p>该方法按字段逐一合并：对每个字段，如果主配置的值非 null 则使用主配置，
     * 否则使用后备配置。这允许从不同来源分层叠加配置。
     *
     * <p><b>合并行为：</b>
     *
     * <ul>
     *   <li>每个字段：primary != null ? primary : fallback</li>
     *   <li>如果 primary 为 null，直接返回 fallback</li>
     *   <li>如果 fallback 为 null，直接返回 primary</li>
     * </ul>
     *
     * <p><b>示例：</b>
     *
     * <pre>{@code
     * ExecutionConfig defaults = ExecutionConfig.MODEL_DEFAULTS;
     * ExecutionConfig agentLevel = ExecutionConfig.builder()
     *     .timeout(Duration.ofMinutes(2))
     *     .build();
     *
     * // 结果：timeout=2 分钟，maxAttempts=3（来自 defaults），...
     * ExecutionConfig merged = ExecutionConfig.mergeConfigs(agentLevel, defaults);
     * }</pre>
     *
     * @param primary  主配置（较高优先级）
     * @param fallback 后备配置（较低优先级）
     * @return 合并后的配置，如果两者均为 null 则返回 null
     */
    public static ExecutionConfig mergeConfigs(ExecutionConfig primary, ExecutionConfig fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }

        Builder builder = builder();
        builder.timeout(primary.timeout != null ? primary.timeout : fallback.timeout);
        builder.maxAttempts(
                primary.maxAttempts != null ? primary.maxAttempts : fallback.maxAttempts);
        builder.initialBackoff(
                primary.initialBackoff != null ? primary.initialBackoff : fallback.initialBackoff);
        builder.maxBackoff(primary.maxBackoff != null ? primary.maxBackoff : fallback.maxBackoff);
        builder.backoffMultiplier(
                primary.backoffMultiplier != null
                        ? primary.backoffMultiplier
                        : fallback.backoffMultiplier);
        builder.retryOn(primary.retryOn != null ? primary.retryOn : fallback.retryOn);

        return builder.build();
    }

    /** ExecutionConfig 的构建器。使用构建器模式构造不可变的配置实例。 */
    public static class Builder {
        private Duration timeout;
        private Integer maxAttempts;
        private Duration initialBackoff;
        private Duration maxBackoff;
        private Double backoffMultiplier;
        private Predicate<Throwable> retryOn;

        /**
         * 设置单次执行的超时时长。
         *
         * @param timeout 超时时长，或 null 表示不设超时
         * @return 当前 Builder 实例
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 设置最大尝试次数（包含首次尝试）。
         *
         * <p>例如 maxAttempts=3 表示：1 次初始尝试 + 2 次重试。
         *
         * @param maxAttempts 最大尝试次数（必须 >= 1），或 null
         * @return 当前 Builder 实例
         */
        public Builder maxAttempts(Integer maxAttempts) {
            if (maxAttempts != null && maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 设置首次重试的初始退避时长。
         *
         * @param initialBackoff 初始退避时长，或 null
         * @return 当前 Builder 实例
         */
        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        /**
         * 设置重试之间的最大退避时长。
         *
         * @param maxBackoff 最大退避时长，或 null
         * @return 当前 Builder 实例
         */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        /**
         * 设置每次重试后的退避乘数（指数退避）。
         *
         * <p>例如 initialBackoff=1s、maxBackoff=10s、backoffMultiplier=2.0 时，
         * 重试延迟序列为：1s、2s、4s、8s、10s（封顶）、10s...
         *
         * @param backoffMultiplier 退避乘数（必须 >= 1.0），或 null
         * @return 当前 Builder 实例
         */
        public Builder backoffMultiplier(Double backoffMultiplier) {
            if (backoffMultiplier != null && backoffMultiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            }
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * 设置判定异常是否应触发重试的断言。
         *
         * @param retryOn 重试断言（返回 true 表示应重试），或 null
         * @return 当前 Builder 实例
         */
        public Builder retryOn(Predicate<Throwable> retryOn) {
            this.retryOn = retryOn;
            return this;
        }

        /**
         * 构建新的 ExecutionConfig 实例。
         *
         * @return 新的 ExecutionConfig 实例
         */
        public ExecutionConfig build() {
            return new ExecutionConfig(this);
        }
    }
}
