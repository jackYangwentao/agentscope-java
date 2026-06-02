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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * 表示带有媒体类型和数据的 Base64 编码媒体内容。
 *
 * <p>此来源用于通过将二进制数据编码为 Base64 字符串，将媒体文件直接嵌入消息中。
 * 格式遵循标准的 MIME 类型约定与 Base64 编码。
 *
 * <p>常见媒体类型包括：
 * <ul>
 *   <li>image/jpeg、image/png、image/gif - 用于图片</li>
 *   <li>audio/mpeg、audio/wav、audio/ogg - 用于音频</li>
 *   <li>video/mp4、video/avi、video/mov - 用于视频</li>
 * </ul>
 *
 * <p>当媒体文件需要直接包含在消息中而非通过 URL 引用时，此方式非常有用。
 */
public class Base64Source extends Source {

    @JsonProperty("media_type")
    private final String mediaType;

    private final String data;

    /**
     * Creates a new Base64 source for JSON deserialization.
     *
     * @param mediaType The MIME type of the media content (e.g., "image/jpeg")
     * @param data The Base64-encoded media data
     * @throws NullPointerException if mediaType or data is null
     */
    @JsonCreator
    public Base64Source(
            @JsonProperty("media_type") String mediaType, @JsonProperty("data") String data) {
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType cannot be null");
        this.data = Objects.requireNonNull(data, "data cannot be null");
    }

    /**
     * Gets the MIME type of the media content.
     *
     * @return The media type as a string (e.g., "image/jpeg")
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Gets the Base64-encoded media data.
     *
     * @return The Base64 data string
     */
    public String getData() {
        return data;
    }

    /**
     * Creates a new builder for constructing Base64Source instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing Base64Source instances.
     */
    public static class Builder {

        private String mediaType;

        private String data;

        /**
         * Sets the MIME type for the media content.
         *
         * @param mediaType The MIME type (e.g., "image/jpeg")
         * @return This builder for chaining
         */
        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        /**
         * Sets the Base64-encoded media data.
         *
         * @param data The Base64 data string
         * @return This builder for chaining
         */
        public Builder data(String data) {
            this.data = data;
            return this;
        }

        /**
         * Builds a new Base64Source with the configured properties.
         *
         * @return A new Base64Source instance
         * @throws NullPointerException if mediaType or data is null
         */
        public Base64Source build() {
            return new Base64Source(mediaType, data);
        }
    }
}
