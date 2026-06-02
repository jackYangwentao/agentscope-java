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

/**
 * 已注册技能的元数据包装类，记录技能在注册表中的状态信息。
 *
 * <p>每个已注册技能对应一个 {@code RegisteredSkill} 实例，追踪以下关键状态：
 * <ul>
 *   <li><b>技能 ID</b>——由技能名称和来源构成，格式为 {@code name_source}</li>
 *   <li><b>激活状态</b>——决定该技能关联的工具组是否对 LLM 可见和可用</li>
 * </ul>
 *
 * <p><b>激活状态管理：</b>
 * <ul>
 *   <li>技能默认以非激活状态注册</li>
 *   <li>当 LLM 通过 {@code load_skill_through_path} 工具加载技能时自动激活</li>
 *   <li>激活后，技能关联的工具组会被启用，LLM 方可调用其中的工具</li>
 *   <li>{@link SkillBox#deactivateAllSkills()} 可在每次智能体调用开始时重置所有技能的激活状态</li>
 * </ul>
 *
 * <p>工具组名称由技能 ID 加上 {@code _skill_tools} 后缀构成，通过 {@link #getToolsGroupName()} 获取。
 *
 * @see SkillRegistry
 * @see SkillBox
 */
class RegisteredSkill {
    private final String skillId;
    private boolean active;

    /**
     * 创建一个新的已注册技能记录，初始状态为非激活。
     *
     * @param skillId 技能的唯一标识符（由名称和来源构成）
     */
    public RegisteredSkill(String skillId) {
        this.skillId = skillId;
        this.active = false;
    }

    /**
     * 设置技能的激活状态。
     *
     * <p>当技能被激活时，其关联的工具组将被启用，LLM 可以调用其中的工具。
     * 当技能被停用时，其工具组将被禁用，LLM 无法访问。
     *
     * @param active true 表示激活该技能，false 表示停用
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 检查技能是否处于激活状态。
     *
     * @return true 表示技能已激活且其工具对 LLM 可用；false 表示未激活
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 获取技能的唯一标识符。
     *
     * @return 技能 ID，格式为 {@code name_source}
     */
    public String getSkillId() {
        return skillId;
    }

    /**
     * 获取该技能关联的工具组名称。
     *
     * <p>工具组名称格式为 {@code <skillId>_skill_tools}，用于在 {@code Toolkit}
     * 中管理该技能的所有工具。当技能激活/停用时，对应的工具组也会被启用/禁用。
     *
     * @return 工具组名称，例如 {@code "my_skill_custom_skill_tools"}
     */
    public String getToolsGroupName() {
        return skillId + "_skill_tools";
    }
}
