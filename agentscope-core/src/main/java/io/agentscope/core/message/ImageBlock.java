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
package io.agentscope.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * 表示消息中带有 URL 或 Base64 来源的图片内容。
 *
 * <p>该内容块支持来自两种来源的图片：
 * <ul>
 *   <li>URL 来源 - 可通过 HTTP/HTTPS URL 或本地文件 URL 访问的图片</li>
 *   <li>Base64 来源 - 使用 MIME 类型编码为 Base64 字符串的图片</li>
 * </ul>
 *
 * <p>图片块对于多模态 AI 交互至关重要，智能体需要处理来自图片、图表、截图
 * 或其他视觉内容的视觉信息。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ImageBlock extends ContentBlock {

    private final Source source;

    private final Integer minPixels;

    private final Integer maxPixels;

    /**
     * Creates a new image block with source.
     *
     * @param source The image source (URL or Base64)
     * @throws NullPointerException if source is null
     */
    public ImageBlock(@JsonProperty("source") Source source) {
        this(source, null, null);
    }

    /**
     * Creates a new image block for JSON deserialization.
     *
     * @param source The image source (URL or Base64)
     * @param minPixels Used to set the minimum pixel threshold for input image.
     * @param maxPixels Used to set the maximum pixel threshold for input image.
     * @throws NullPointerException if source is null
     */
    @JsonCreator
    private ImageBlock(
            @JsonProperty("source") Source source,
            @JsonProperty("min_pixels") Integer minPixels,
            @JsonProperty("max_pixels") Integer maxPixels) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.minPixels = minPixels;
        this.maxPixels = maxPixels;
    }

    /**
     * Gets the source of this image.
     *
     * @return The image source containing URL or Base64 data
     */
    public Source getSource() {
        return source;
    }

    /**
     * Gets the minimum pixel threshold for input image.
     *
     * @return The minimum pixel threshold
     */
    public Integer getMinPixels() {
        return minPixels;
    }

    /**
     * Gets the maximum pixel threshold for input image.
     *
     * @return The maximum pixel threshold
     */
    public Integer getMaxPixels() {
        return maxPixels;
    }

    /**
     * Creates a new builder for constructing ImageBlock instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing ImageBlock instances.
     */
    public static class Builder {

        private Source source;

        private Integer minPixels;

        private Integer maxPixels;

        /**
         * Sets the source for the image.
         *
         * @param source The image source (URL or Base64)
         * @return This builder for chaining
         */
        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the minimum pixel threshold for input image frames.
         *
         * @param minPixels The minimum pixel threshold
         * @return This builder for chaining
         */
        public Builder minPixels(Integer minPixels) {
            this.minPixels = minPixels;
            return this;
        }

        /**
         * Sets the maximum pixel threshold for input image frames.
         *
         * @param maxPixels The maximum pixel threshold
         * @return This builder for chaining
         */
        public Builder maxPixels(Integer maxPixels) {
            this.maxPixels = maxPixels;
            return this;
        }

        /**
         * Builds a new ImageBlock with the configured source.
         *
         * @return A new ImageBlock instance
         * @throws NullPointerException if source is null
         */
        public ImageBlock build() {
            return new ImageBlock(source, minPixels, maxPixels);
        }
    }
}
