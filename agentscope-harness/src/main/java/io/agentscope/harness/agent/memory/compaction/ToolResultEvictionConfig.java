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
package io.agentscope.harness.agent.memory.compaction;

import java.util.Set;

/**
 * 按工具结果驱逐超大输出的配置。
 *
 * <p>当工具产生的结果文本内容超过 {@link #getMaxResultChars()} 时，
 * 完整输出将被写入工作区文件系统抽象中 {@link #getEvictionPath()} 下确定性的路径，
 * 上下文中的 {@link io.agentscope.core.message.ToolResultBlock} 将被替换为紧凑的占位符，
 * 包含首尾预览和使用 {@code readFile} 获取完整内容的指令。
 *
 * <p>此机制与对话摘要（{@link CompactionConfig}）<b>正交</b>：
 * <ul>
 *   <li><b>驱逐</b> 解决上下文<em>宽度</em>——单个消息过大。</li>
 *   <li><b>压缩</b> 解决上下文<em>深度</c>——累积消息过多。</li>
 * </ul>
 * 两者在不同的触发条件和不同的生命周期事件上独立运行。
 *
 * <ul>
 *   <li>80,000 个字符时触发（约 4 字符/令牌下的 20K 令牌）</li>
 *   <li>预览：原始输出的前 + 后各 2,000 个字符</li>
 *   <li>驱逐路径前缀：{@code /large_tool_results}</li>
 *   <li>排除的工具：文件系统读/写/编辑/列出 + 内存工具（小或自分页）</li>
 * </ul>
 */
public class ToolResultEvictionConfig {

    /** ~20 K tokens × 4 chars/token — default eviction threshold. */
    public static final int DEFAULT_MAX_RESULT_CHARS = 80_000;

    /** Characters to show at head and tail in the eviction placeholder preview. */
    public static final int DEFAULT_PREVIEW_CHARS = 2_000;

    /** Root path prefix under which evicted results are stored. */
    public static final String DEFAULT_EVICTION_PATH = "/large_tool_results";

    /**
     * Tools excluded from eviction by default.
     *
     * <ul>
     *   <li>{@code read_file} — evicting would cause re-read loops; pagination handles size</li>
     *   <li>{@code write_file}, {@code edit_file} — return tiny success messages</li>
     *   <li>{@code grep_files}, {@code glob_files}, {@code list_files} — self-limiting outputs</li>
     *   <li>{@code memory_search}, {@code memory_get}, {@code session_search} — small/paginated results</li>
     * </ul>
     *
     * Shell ({@code execute}) is intentionally NOT excluded: command output can be very large.
     */
    public static final Set<String> DEFAULT_EXCLUDED_TOOLS =
            Set.of(
                    "read_file",
                    "write_file",
                    "edit_file",
                    "grep_files",
                    "glob_files",
                    "list_files",
                    "memory_search",
                    "memory_get",
                    "session_search");

    private final int maxResultChars;
    private final int previewChars;
    private final String evictionPath;
    private final Set<String> excludedToolNames;

    private ToolResultEvictionConfig(Builder builder) {
        this.maxResultChars = builder.maxResultChars;
        this.previewChars = builder.previewChars;
        this.evictionPath = builder.evictionPath;
        this.excludedToolNames = builder.excludedToolNames;
    }

    /** Creates a config with all defaults applied. */
    public static ToolResultEvictionConfig defaults() {
        return new Builder().build();
    }

    /** Maximum text length (chars) before eviction fires. */
    public int getMaxResultChars() {
        return maxResultChars;
    }

    /** Characters to show in the head and tail preview. */
    public int getPreviewChars() {
        return previewChars;
    }

    /** Root path under which evicted files are written (e.g. {@code /large_tool_results}). */
    public String getEvictionPath() {
        return evictionPath;
    }

    /** Tool names that will never be evicted regardless of result size. */
    public Set<String> getExcludedToolNames() {
        return excludedToolNames;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ToolResultEvictionConfig}. */
    public static class Builder {

        private int maxResultChars = DEFAULT_MAX_RESULT_CHARS;
        private int previewChars = DEFAULT_PREVIEW_CHARS;
        private String evictionPath = DEFAULT_EVICTION_PATH;
        private Set<String> excludedToolNames = DEFAULT_EXCLUDED_TOOLS;

        /** Sets the character threshold above which eviction is triggered. */
        public Builder maxResultChars(int maxResultChars) {
            this.maxResultChars = maxResultChars;
            return this;
        }

        /** Sets how many characters to include in the head/tail preview. */
        public Builder previewChars(int previewChars) {
            this.previewChars = previewChars;
            return this;
        }

        /** Sets the root filesystem path prefix for evicted files. */
        public Builder evictionPath(String evictionPath) {
            this.evictionPath = evictionPath;
            return this;
        }

        /** Replaces the default set of excluded tool names. */
        public Builder excludedToolNames(Set<String> excludedToolNames) {
            this.excludedToolNames = Set.copyOf(excludedToolNames);
            return this;
        }

        public ToolResultEvictionConfig build() {
            return new ToolResultEvictionConfig(this);
        }
    }
}
