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
 * 表示基于 URL 的媒体内容。
 *
 * <p>此来源引用可通过 URL 访问的媒体文件，支持远程 HTTP/HTTPS URL 和本地文件 URL。
 * 当媒体文件托管在外部或文件大小使 Base64 编码不切实际时，推荐使用此方式。
 *
 * <p>支持的 URL 格式：
 * <ul>
 *   <li>远程 URL：https://example.com/image.jpg</li>
 *   <li>本地文件：file:///absolute/path/to/file.jpg</li>
 * </ul>
 *
 * <p>使用 URL 来源对于大型媒体文件更有效率，允许系统流式传输内容而不是将所有内容加载到内存中。
 */
public class URLSource extends Source {

    private final String url;

    /**
     * Creates a new URL source for JSON deserialization.
     *
     * @param url The URL pointing to the media content
     * @throws NullPointerException if url is null
     */
    @JsonCreator
    public URLSource(@JsonProperty("url") String url) {
        this.url = Objects.requireNonNull(url, "url cannot be null");
    }

    /**
     * Gets the URL that points to the media content.
     *
     * @return The URL as a string
     */
    public String getUrl() {
        return url;
    }

    /**
     * Creates a new builder for constructing URLSource instances.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing URLSource instances.
     */
    public static class Builder {

        private String url;

        /**
         * Sets the URL for the media content.
         *
         * @param url The URL pointing to the media content
         * @return This builder for chaining
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Builds a new URLSource with the configured URL.
         *
         * @return A new URLSource instance
         * @throws NullPointerException if url is null
         */
        public URLSource build() {
            return new URLSource(url);
        }
    }
}
