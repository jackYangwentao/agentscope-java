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

package io.agentscope.core.skill;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 表示可被智能体加载和使用的技能。
 *
 * <p>技能由以下部分组成：
 * <ul>
 *   <li>名称和描述——标识技能</li>
 *   <li>技能内容——实际的技能实现或指令</li>
 *   <li>资源——技能引用的支持文件或数据</li>
 *   <li>版本和来源——追踪技能来源和版本信息</li>
 * </ul>
 *
 * <p><b>创建方式：</b>
 * <ul>
 *   <li>从带 YAML 前置元数据的 Markdown——自动提取元数据</li>
 *   <li>从显式参数——直接使用所有字段构造</li>
 *   <li>从 Builder——用于创建现有技能的修改版本</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // From markdown with frontmatter (use SkillUtil)
 * String skillMd = "---\nname: my_skill\ndescription: Does something\n---\nContent here";
 * Map<String, String> resources = Map.of("file1.txt", "content1");
 * AgentSkill skill = SkillUtil.createFrom(skillMd, resources);
 *
 * // Direct construction
 * AgentSkill skill2 = new AgentSkill("my_skill", "Does something", "Content here", resources);
 *
 * // Create modified version using builder
 * AgentSkill modified = skill.toBuilder()
 *     .description("Updated description")
 *     .skillContent("Modified instructions")
 *     .addResource("config.json", "{\"key\": \"value\"}")
 *     .build();
 * }</pre>
 *
 * @see io.agentscope.core.skill.util.SkillUtil
 * @see io.agentscope.core.skill.util.MarkdownSkillParser
 */
public class AgentSkill {
    private final Map<String, Object> metadata;
    private final String skillContent;
    private final Map<String, String> resources;
    private final String source;

    /**
     * 创建一个包含显式参数的技能实例，使用默认来源标识 "custom"。
     *
     * <p>当你希望直接通过参数创建技能而无需解析 Markdown 时使用此构造器。
     *
     * @param name         技能名称（不可为 null 或空字符串）
     * @param description  技能描述（不可为 null 或空字符串）
     * @param skillContent 技能实现内容或指令（不可为 null 或空字符串）
     * @param resources    技能引用的支持资源（可以为 null）
     * @throws IllegalArgumentException 如果 name、description 或 skillContent 为 null 或空
     */
    public AgentSkill(
            String name, String description, String skillContent, Map<String, String> resources) {
        this(name, description, skillContent, resources, "custom");
    }

    /**
     * 创建一个包含显式参数和自定义来源标识的技能实例。
     *
     * <p>来源标识指示技能的出处，可用于区分来自不同仓库或位置的同一名称技能。
     *
     * @param name         技能名称（不可为 null 或空字符串）
     * @param description  技能描述（不可为 null 或空字符串）
     * @param skillContent 技能实现内容或指令（不可为 null 或空字符串）
     * @param resources    技能引用的支持资源（可以为 null）
     * @param source       技能来源标识（null 默认使用 "custom"）
     * @throws IllegalArgumentException 如果 name、description 或 skillContent 为 null 或空
     */
    public AgentSkill(
            String name,
            String description,
            String skillContent,
            Map<String, String> resources,
            String source) {
        this(createMetadata(name, description), skillContent, resources, source);
    }

    /**
     * 使用显式元数据映射创建技能实例。
     *
     * <p>元数据映射必须包含非空字符串类型的 {@code name} 和 {@code description} 字段。
     * 传入的元数据映射会被复制并存储为不可修改的副本。资源映射同样会被复制。
     *
     * @param metadata     技能元数据，必须包含 {@code name} 和 {@code description} 键
     * @param skillContent 技能实现内容或指令（不可为 null 或空字符串）
     * @param resources    技能引用的支持资源（可以为 null）
     * @param source       技能来源标识（null 默认使用 "custom"）
     * @throws IllegalArgumentException 如果 metadata 无效或 skillContent 为 null 或空
     */
    public AgentSkill(
            Map<String, Object> metadata,
            String skillContent,
            Map<String, String> resources,
            String source) {
        String name = getRequiredMetadataString(metadata, "name");
        String description = getRequiredMetadataString(metadata, "description");
        if (skillContent == null || skillContent.isEmpty()) {
            throw new IllegalArgumentException("The skill must have content");
        }

        LinkedHashMap<String, Object> metadataCopy = new LinkedHashMap<>(metadata);
        metadataCopy.put("name", name);
        metadataCopy.put("description", description);

        this.metadata = Collections.unmodifiableMap(metadataCopy);
        this.skillContent = skillContent;
        this.resources = resources != null ? new HashMap<>(resources) : new HashMap<>();
        this.source = source != null ? source : "custom";
    }

    /**
     * 获取技能名称。
     *
     * @return 技能名称（不可为 null）
     */
    public String getName() {
        return (String) metadata.get("name");
    }

    /**
     * 获取技能描述。
     *
     * @return 技能描述（不可为 null）
     */
    public String getDescription() {
        return (String) metadata.get("description");
    }

    /**
     * 获取技能的不可修改元数据映射。
     *
     * <p>元数据包含创建时传入的所有键值对，至少包含 {@code name} 和 {@code description} 字段。
     *
     * @return 不可修改的元数据映射（不可为 null，除必需字段外可能为空）
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 根据键获取元数据中的某个值。
     *
     * @param key 元数据键名
     * @return 元数据值，如果未找到则返回 null
     */
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }

    /**
     * 获取技能内容。
     *
     * <p>技能内容是技能的核心实现或指令文本，描述了智能体应如何执行该技能。
     *
     * @return 技能内容（不可为 null）
     */
    public String getSkillContent() {
        return skillContent;
    }

    /**
     * 获取技能的来源标识符。
     *
     * @return 来源标识符（不可为 null）
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取技能引用的所有支持资源。
     *
     * <p>返回的资源映射是原映射的副本，调用方对其的修改不会影响技能实例。
     *
     * @return 资源映射副本（不可为 null，可能为空）
     */
    public Map<String, String> getResources() {
        return new HashMap<>(resources);
    }

    /**
     * 根据路径获取指定资源的内容。
     *
     * @param resourcePath 资源路径
     * @return 资源内容，如果未找到则返回 null
     */
    public String getResource(String resourcePath) {
        return resources.get(resourcePath);
    }

    /**
     * 获取技能所有资源的路径集合。
     *
     * @return 不可修改的资源路径集合
     */
    public Set<String> getResourcePaths() {
        return Collections.unmodifiableSet(new HashSet<>(resources.keySet()));
    }

    /**
     * 获取技能的唯一标识符。
     *
     * <p>标识符由技能名称和来源组成，格式为 {@code name_source}，用于在注册表中
     * 区分来自不同来源的同名技能。
     *
     * @return 技能唯一标识符（不可为 null）
     */
    public String getSkillId() {
        return getName() + "_" + source;
    }

    /**
     * 基于当前技能创建一个 Builder，用于生成当前技能的修改版本。
     *
     * <p>Builder 会预填充当前技能的所有字段值，调用方只需设置需要修改的字段即可。
     *
     * @return 新的 Builder 实例，已用当前技能的值初始化
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * 创建一个空的 Builder，用于从零开始构建技能。
     *
     * @return 新的空 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回技能的字符串表示形式。
     *
     * @return 包含名称、描述和来源的字符串
     */
    @Override
    public String toString() {
        return "AgentSkill{name='"
                + getName()
                + "', description='"
                + getDescription()
                + "', source='"
                + source
                + "'}";
    }

    /**
     * 用于创建 {@link AgentSkill} 实例的构建器。
     *
     * <p>该构建器支持选择性修改技能字段，既可以基于现有技能创建修改版本，
     * 也可以从零开始创建新技能。通过链式调用（fluent API）提供流畅的构建体验。
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * // 从零创建
     * AgentSkill skill = AgentSkill.builder()
     *     .name("my_skill")
     *     .description("Does something")
     *     .skillContent("Instructions here")
     *     .addResource("file.txt", "content")
     *     .build();
     *
     * // 修改现有技能
     * AgentSkill modified = existingSkill.toBuilder()
     *     .description("Updated description")
     *     .addResource("new_file.txt", "new content")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private Map<String, Object> metadata;
        private String skillContent;
        private Map<String, String> resources;
        private String source;

        /**
         * 创建一个空的构建器，所有字段均为默认值。
         */
        private Builder() {
            this.metadata = new LinkedHashMap<>();
            this.resources = new HashMap<>();
        }

        /**
         * 创建一个使用现有技能值初始化的构建器。
         *
         * <p>元数据、技能内容、资源和来源均从基础技能复制。
         *
         * @param baseSkill 要复制值的基础技能
         */
        private Builder(AgentSkill baseSkill) {
            this.metadata = new LinkedHashMap<>(baseSkill.metadata);
            this.skillContent = baseSkill.skillContent;
            this.resources = new HashMap<>(baseSkill.resources);
            this.source = baseSkill.source;
        }

        /**
         * 设置技能名称。
         *
         * @param name 技能名称
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder name(String name) {
            this.metadata.put("name", name);
            return this;
        }

        /**
         * 设置技能描述。
         *
         * @param description 技能描述
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder description(String description) {
            this.metadata.put("description", description);
            return this;
        }

        /**
         * 用新的映射替换所有元数据。
         *
         * @param metadata 新的元数据映射
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata =
                    metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
            return this;
        }

        /**
         * 添加或更新单条元数据条目。
         *
         * @param key   元数据键名
         * @param value 元数据值
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder putMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * 移除指定键的元数据条目。
         *
         * @param key 要移除的元数据键名
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder removeMetadata(String key) {
            this.metadata.remove(key);
            return this;
        }

        /**
         * 设置技能内容。
         *
         * @param skillContent 技能内容/指令文本
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder skillContent(String skillContent) {
            this.skillContent = skillContent;
            return this;
        }

        /**
         * 用新的映射替换所有资源。
         *
         * @param resources 新的资源映射
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder resources(Map<String, String> resources) {
            this.resources = new HashMap<>(resources);
            return this;
        }

        /**
         * 添加或更新单条资源。
         *
         * @param path    资源路径
         * @param content 资源内容
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder addResource(String path, String content) {
            this.resources.put(path, content);
            return this;
        }

        /**
         * 移除指定路径的资源。
         *
         * @param path 要移除的资源路径
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder removeResource(String path) {
            this.resources.remove(path);
            return this;
        }

        /**
         * 清空所有资源。
         *
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder clearResources() {
            this.resources.clear();
            return this;
        }

        /**
         * 设置来源标识符。
         *
         * @param source 来源标识符
         * @return 当前构建器实例（用于链式调用）
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * 构建 {@link AgentSkill} 实例。
         *
         * <p>构建时会校验元数据中是否包含有效的 {@code name} 和 {@code description}，
         * 以及技能内容是否非空。
         *
         * @return 新的 {@link AgentSkill} 实例
         * @throws IllegalArgumentException 如果缺少必填字段
         */
        public AgentSkill build() {
            return new AgentSkill(metadata, skillContent, resources, source);
        }
    }

    private static Map<String, Object> createMetadata(String name, String description) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        metadata.put("description", description);
        return metadata;
    }

    private static String getRequiredMetadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "The skill must have `name` and `description` fields.");
        }

        Object value = metadata.get(key);
        if (!(value instanceof String stringValue) || stringValue.isEmpty()) {
            throw new IllegalArgumentException(
                    "The skill must have `name` and `description` fields.");
        }
        return stringValue;
    }
}
