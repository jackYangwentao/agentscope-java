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
package io.agentscope.core.skill.repository;

/**
 * 技能仓库的元数据信息，描述仓库的类型、位置和写入能力。
 *
 * <p>每个 {@link AgentSkillRepository} 实现都应通过 {@code getRepositoryInfo()} 方法
 * 返回一个该类的实例，以便外部代码了解仓库的底层存储特性和访问能力。
 *
 * <p><b>仓库类型示例：</b>
 * <ul>
 *   <li>{@code filesystem} —— 本地文件系统存储</li>
 *   <li>{@code classpath} —— Java 类路径/JAR 包资源</li>
 *   <li>{@code github} —— GitHub 仓库</li>
 *   <li>{@code mysql} —— MySQL 数据库</li>
 *   <li>{@code redis} —— Redis 缓存</li>
 *   <li>自定义实现类型</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * AgentSkillRepositoryInfo info = new AgentSkillRepositoryInfo(
 *     "filesystem",
 *     "/path/to/skills",
 *     true
 * );
 * System.out.println(info.getType()); // "filesystem"
 * }</pre>
 */
public class AgentSkillRepositoryInfo {
    private final String type;
    private final String location;
    private final boolean writable;

    /**
     * 创建一个新的仓库元数据实例。
     *
     * @param type 仓库类型标识，如 "filesystem"、"classpath" 等（不可为 null）
     * @param location 仓库位置路径（不可为 null），如文件系统路径或资源路径
     * @param writable 仓库是否支持写入操作（保存、删除技能）
     */
    public AgentSkillRepositoryInfo(String type, String location, boolean writable) {
        this.type = type;
        this.location = location;
        this.writable = writable;
    }

    /**
     * 获取仓库类型标识。
     *
     * @return 仓库类型，如 "filesystem"、"classpath" 等（不可为 null）
     */
    public String getType() {
        return type;
    }

    /**
     * 获取仓库位置。
     *
     * @return 仓库位置路径（不可为 null），文件系统仓库返回目录路径，类路径仓库返回资源路径
     */
    public String getLocation() {
        return location;
    }

    /**
     * 检查仓库是否支持写入操作。
     *
     * <p>只读仓库（如从类路径加载的仓库）不支持保存或删除技能。
     *
     * @return {@code true} 表示支持写入操作；{@code false} 表示只读
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * 返回该仓库元数据的字符串表示形式。
     *
     * @return 格式为 {@code "AgentSkillRepositoryInfo{type='...', location='...', writable=...}"}
     */
    @Override
    public String toString() {
        return String.format(
                "AgentSkillRepositoryInfo{type='%s', location='%s', writable=%s}",
                type, location, writable);
    }
}
