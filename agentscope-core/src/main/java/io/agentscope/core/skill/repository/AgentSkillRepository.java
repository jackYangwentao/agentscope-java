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

import io.agentscope.core.skill.AgentSkill;
import java.util.List;

/**
 * 技能仓库接口，定义 AgentSkill 持久化操作契约。
 *
 * <p>该接口遵循仓库模式（Repository Pattern）和依赖反转原则（Dependency Inversion Principle），
 * 允许不同的存储后端（文件系统、数据库、远程 API 等）互换使用。
 *
 * <p><b>核心操作：</b>
 * <ul>
 *   <li>查询——按名称获取单个技能、列出所有技能、检查技能是否存在</li>
 *   <li>写入——保存技能（含强制覆盖选项）、按名称删除技能</li>
 *   <li>元数据——获取仓库类型/位置信息、获取来源标识</li>
 *   <li>资源管理——通过扩展 {@link AutoCloseable} 支持 try-with-resources 自动释放资源</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * AgentSkillRepository repo = new FileSystemSkillRepository(Paths.get("/path/to/skills"));
 * AgentSkill skill = repo.getSkill("my_skill");
 * repo.save(List.of(skill), false);
 * repo.close();
 * }</pre>
 *
 * @see FileSystemSkillRepository
 * @see ClasspathSkillRepository
 */
public interface AgentSkillRepository extends AutoCloseable {

    /**
     * 根据技能名称获取对应的技能实例。
     *
     * <p>具体查找方式由实现类决定。文件系统实现会扫描各子目录的 SKILL.md 元数据来匹配名称。
     *
     * @param name 技能名称（不可为 null）
     * @return 匹配的技能实例
     * @throws IllegalArgumentException 如果名称为 null、无效或未找到对应技能
     */
    AgentSkill getSkill(String name);

    /**
     * 获取仓库中所有可用的技能名称列表。
     *
     * @return 所有技能名称的列表（不可为 null，可能为空列表）
     */
    List<String> getAllSkillNames();

    /**
     * 获取仓库中所有可用的技能实例列表。
     *
     * @return 所有技能实例的列表（不可为 null，可能为空列表）
     */
    List<AgentSkill> getAllSkills();

    /**
     * 保存或更新仓库中的技能。
     *
     * <p>如果同名的技能已存在，默认跳过（除非 force=true 强制覆盖）。
     * 如果技能列表为空，返回 false。
     *
     * @param skills 要保存的技能列表（不可为 null）
     * @param force  是否强制覆盖已存在的技能，true 表示覆盖，false 表示跳过已存在的
     * @return {@code true} 表示所有技能均保存成功，{@code false} 表示部分或全部保存失败
     */
    boolean save(List<AgentSkill> skills, boolean force);

    /**
     * 根据技能名称从仓库中删除对应的技能。
     *
     * @param skillName 要删除的技能名称（不可为 null）
     * @return {@code true} 表示删除成功；{@code false} 表示未找到该技能
     */
    boolean delete(String skillName);

    /**
     * 检查仓库中是否存在指定名称的技能。
     *
     * @param skillName 技能名称（不可为 null）
     * @return {@code true} 表示技能存在；{@code false} 表示不存在
     */
    boolean skillExists(String skillName);

    /**
     * 获取仓库的元数据信息。
     *
     * <p>返回的信息包括仓库类型（如 filesystem、classpath）、位置路径以及其他元数据。
     *
     * @return 仓库信息对象（不可为 null）
     */
    AgentSkillRepositoryInfo getRepositoryInfo();

    /**
     * 获取仓库的来源标识符。
     *
     * <p>来源标识用于区分来自不同仓库的技能，格式通常为 {@code repositoryType_location}。
     * 例如：{@code "filesystem_skills"}、{@code "classpath-writing-skills"}。
     *
     * @return 来源标识符（不可为 null）
     */
    String getSource();

    /**
     * 设置仓库的可写标志。
     *
     * @param writeable true 表示支持写入操作（保存、删除），false 表示只读
     */
    void setWriteable(boolean writeable);

    /**
     * 检查仓库是否支持写入操作。
     *
     * @return {@code true} 表示可写；{@code false} 表示只读
     */
    boolean isWriteable();

    /**
     * 释放仓库使用的资源。
     *
     * <p>实现类应在此方法中释放网络连接、文件句柄、缓存等资源。
     * 默认实现为空操作。推荐使用 try-with-resources 语法自动调用。
     */
    @Override
    default void close() {
        // 默认实现不执行任何操作
    }
}
