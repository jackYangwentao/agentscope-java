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
package io.agentscope.core.skill;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册表，管理技能实例的注册、检索、激活状态和移除操作。
 *
 * <p>该类是技能管理系统的纯存储层，内部维护两个并发的映射：
 * <ul>
 *   <li>{@code skills} —— 技能 ID 到 {@link AgentSkill} 实例的映射</li>
 *   <li>{@code registeredSkills} —— 技能 ID 到 {@link RegisteredSkill} 元数据包装的映射</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>注册和注销技能（替换已存在的技能）</li>
 *   <li>按技能 ID 检索技能实例和注册元数据</li>
 *   <li>管理单个或全部技能的激活/停用状态</li>
 *   <li>提供技能存在性检查</li>
 * </ul>
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>这是一个纯存储层，不涉及业务逻辑验证</li>
 *   <li>使用 {@link ConcurrentHashMap} 保证线程安全</li>
 *   <li>除非明确说明，所有参数均假定为非 null，参数验证由调用方（如 {@link SkillBox}）负责</li>
 * </ul>
 *
 * @see SkillBox
 * @see RegisteredSkill
 */
class SkillRegistry {
    private final Map<String, AgentSkill> skills = new ConcurrentHashMap<>();
    private final Map<String, RegisteredSkill> registeredSkills = new ConcurrentHashMap<>();

    // ==================== 注册操作 ====================

    /**
     * 注册一个技能及其关联的注册元数据。
     *
     * <p>如果指定的 {@code skillId} 已经注册过，对应的技能和元数据将被新值替换。
     * 此方法不执行任何校验，调用方应确保参数有效。
     *
     * @param skillId   技能的唯一标识符（不可为 null）
     * @param skill     技能实例（不可为 null）
     * @param registered 技能的注册元数据包装（不可为 null）
     */
    void registerSkill(String skillId, AgentSkill skill, RegisteredSkill registered) {
        skills.put(skillId, skill);
        registeredSkills.put(skillId, registered);
    }

    // ==================== 激活状态管理 ====================

    /**
     * 设置指定技能的激活状态。
     *
     * <p>激活状态控制该技能关联的工具组是否对 LLM 可见。
     *
     * @param skillId 技能 ID（不可为 null）
     * @param active  true 表示激活该技能，false 表示停用
     */
    void setSkillActive(String skillId, boolean active) {
        RegisteredSkill registered = registeredSkills.get(skillId);
        if (registered != null) {
            registered.setActive(active);
        }
    }

    /**
     * 设置所有注册技能的激活状态。
     *
     * <p>用于在智能体每次调用开始时统一重置状态，确保干净的执行环境。
     *
     * @param active true 表示激活所有技能，false 表示停用所有技能
     */
    void setAllSkillsActive(boolean active) {
        registeredSkills.values().forEach(r -> r.setActive(active));
    }

    // ==================== 查询操作 ====================

    /**
     * 根据技能 ID 获取技能实例。
     *
     * @param skillId 技能 ID（不可为 null）
     * @return 技能实例，如果未找到则返回 null
     */
    AgentSkill getSkill(String skillId) {
        return skills.get(skillId);
    }

    /**
     * 根据技能 ID 获取已注册技能的元数据包装。
     *
     * @param skillId 技能 ID（不可为 null）
     * @return 已注册技能的元数据包装，如果未找到则返回 null
     */
    RegisteredSkill getRegisteredSkill(String skillId) {
        return registeredSkills.get(skillId);
    }

    /**
     * 获取所有已注册技能的 ID 集合。
     *
     * @return 技能 ID 的集合副本（不可为 null，可能为空）
     */
    Set<String> getSkillIds() {
        return new HashSet<>(skills.keySet());
    }

    /**
     * 检查指定技能 ID 是否已注册。
     *
     * @param skillId 技能 ID（不可为 null）
     * @return true 表示技能已注册，false 表示未注册
     */
    boolean exists(String skillId) {
        return skills.containsKey(skillId);
    }

    /**
     * 获取所有已注册技能的元数据映射。
     *
     * @return 技能 ID 到注册元数据的映射副本（不可为 null，可能为空）
     */
    Map<String, RegisteredSkill> getAllRegisteredSkills() {
        return new ConcurrentHashMap<>(registeredSkills);
    }

    // ==================== 移除操作 ====================

    /**
     * 完全移除一个技能，包括技能实例和注册元数据。
     *
     * @param skillId 要移除的技能 ID（不可为 null）
     */
    void removeSkill(String skillId) {
        skills.remove(skillId);
        registeredSkills.remove(skillId);
    }
}
