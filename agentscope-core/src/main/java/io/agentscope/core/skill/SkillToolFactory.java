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

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 技能访问工具工厂，用于创建 {@code load_skill_through_path} 工具，
 * 使 LLM 能够动态加载和激活技能资源。
 *
 * <p>该工厂创建的工具有以下关键行为：
 * <ul>
 *   <li>根据技能 ID 和资源路径加载技能资源内容</li>
 *   <li>加载 SKILL.md 时自动激活技能，使其关联的工具组对 LLM 可用</li>
 *   <li>支持加载技能包内的任意资源文件（脚本、配置、模板等）</li>
 *   <li>当请求的资源不存在时，返回可用资源列表引导 LLM 修正请求</li>
 * </ul>
 *
 * <p><b>生命周期：</b>工厂内部持有 {@link Toolkit} 引用，由于 {@code ReActAgent}
 * 使用 {@code Toolkit} 的深拷贝，因此提供 {@link #bindToolkit(Toolkit)} 方法
 * 在代理拷贝后重新绑定正确的工具包实例。
 *
 * @see SkillBox
 * @see AgentSkill
 */
class SkillToolFactory {

    private static final Logger logger = LoggerFactory.getLogger(SkillToolFactory.class);

    private final SkillRegistry skillRegistry;
    private Toolkit toolkit;

    SkillToolFactory(SkillRegistry skillRegistry, Toolkit toolkit) {
        this.skillRegistry = skillRegistry;
        this.toolkit = toolkit;
    }

    /**
     * 将工具包绑定到技能工具工厂。
     *
     * <p>由于 {@code ReActAgent} 使用 {@code Toolkit} 的深拷贝，在代理创建后
     * 需要调用此方法重新绑定，确保技能工具工厂引用的是正确的工具包实例，
     * 从而能够正确管理技能关联的工具组。
     *
     * @param toolkit 要绑定的工具包实例（不可为 null）
     * @throws IllegalArgumentException 如果 toolkit 为 null
     */
    void bindToolkit(Toolkit toolkit) {
        this.toolkit = toolkit;
    }

    /**
     * 创建 {@code load_skill_through_path} 智能体工具。
     *
     * <p>该工具允许智能体按技能 ID 和资源路径加载并激活技能资源。
     * 支持加载 SKILL.md 获取技能文档，或加载其他资源如脚本、配置和模板。
     *
     * <p><b>工具参数：</b>
     * <ul>
     *   <li>{@code skillId}（必填）—— 技能的唯一标识符，通过枚举值限定可选范围</li>
     *   <li>{@code path}（必填）—— 资源路径，使用 "SKILL.md" 加载技能文档，或使用技能中列出的具体路径</li>
     * </ul>
     *
     * @return 用于加载技能资源的 {@link AgentTool} 实例
     */
    AgentTool createSkillAccessToolAgentTool() {
        return new AgentTool() {
            @Override
            public String getName() {
                return "load_skill_through_path";
            }

            @Override
            public String getDescription() {
                return "Load and activate a skill resource by its ID and resource path.\n\n"
                        + "**Functionality:**\n"
                        + "1. Activates the specified skill (making its tools available)\n"
                        + "2. Returns the requested resource content\n"
                        + "\n"
                        + "**Path rules:**\n"
                        + "- Use path=\"SKILL.md\" to load the skill's markdown documentation"
                        + " (name, description, usage instructions).\n"
                        + "- Use exact resource paths listed by the skill, such as"
                        + " \"references/guide.md\" or \"scripts/run.py\".\n"
                        + "- Do not use '.', './', the skill directory, or an absolute path.";
            }

            @Override
            public Map<String, Object> getParameters() {
                // Get all available skill IDs
                List<String> availableSkillIds =
                        new ArrayList<>(skillRegistry.getAllRegisteredSkills().keySet());

                return Map.of(
                        "type", "object",
                        "properties",
                                Map.of(
                                        "skillId",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "The unique identifier of the" + " skill.",
                                                        "enum",
                                                        availableSkillIds),
                                        "path",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "The exact resource path within the skill."
                                                                + " Use 'SKILL.md' to load the"
                                                                + " skill instructions. Do not use"
                                                                + " '.', './', directories, or"
                                                                + " absolute paths.")),
                        "required", List.of("skillId", "path"));
            }

            @Override
            public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
                try {
                    Map<String, Object> input = param.getInput();

                    // Validate parameters
                    String skillId = (String) input.get("skillId");
                    if (skillId == null || skillId.trim().isEmpty()) {
                        return Mono.just(
                                ToolResultBlock.error(
                                        "Missing or empty required parameter: skillId"));
                    }

                    String path = (String) input.get("path");
                    if (path == null || path.trim().isEmpty()) {
                        return Mono.just(
                                ToolResultBlock.error("Missing or empty required parameter: path"));
                    }

                    String result = loadSkillResourceImpl(skillId, path);
                    return Mono.just(ToolResultBlock.text(result));
                } catch (IllegalArgumentException e) {
                    logger.error("Error loading skill resource", e);
                    return Mono.just(ToolResultBlock.error(e.getMessage()));
                } catch (Exception e) {
                    logger.error("Unexpected error loading skill resource", e);
                    return Mono.just(ToolResultBlock.error(e.getMessage()));
                }
            }
        };
    }

    /**
     * 技能资源加载的核心实现。
     *
     * <p>处理以下场景：
     * <ul>
     *   <li>路径为 "SKILL.md" —— 自动激活技能并返回技能文档（名称、描述、使用说明）</li>
     *   <li>路径为资源文件 —— 返回对应资源内容并在首次加载时激活技能</li>
     *   <li>路径不存在 —— 抛出 {@link IllegalArgumentException}，附上可用资源列表引导 LLM</li>
     * </ul>
     *
     * @param skillId 技能的唯一标识符
     * @param path    资源文件路径
     * @return 格式化后的资源内容
     * @throws IllegalArgumentException 如果技能不存在或资源未找到
     */
    private String loadSkillResourceImpl(String skillId, String path) {
        AgentSkill skill = validateSkillExists(skillId);

        // Special handling for SKILL.md - return the skill's markdown content
        if ("SKILL.md".equals(path)) {
            activateSkill(skillId);
            return buildSkillMarkdownResponse(skillId, skill);
        }

        // Get resource
        Map<String, String> resources = skill.getResources();
        if (resources == null || !resources.containsKey(path)) {
            // Resource not found, return available resource paths
            throw new IllegalArgumentException(
                    buildResourceNotFoundMessage(skillId, path, resources));
        }

        String resourceContent = resources.get(path);
        activateSkill(skillId);
        return buildResourceResponse(skillId, path, resourceContent);
    }

    /**
     * 构建 SKILL.md 内容的响应，包含技能激活确认和完整文档。
     *
     * @param skillId 技能 ID
     * @param skill   技能实例
     * @return 格式化后的技能 Markdown 响应
     */
    private String buildSkillMarkdownResponse(String skillId, AgentSkill skill) {
        StringBuilder result = new StringBuilder();
        result.append("Successfully loaded skill: ").append(skillId).append("\n\n");
        result.append("Name: ").append(skill.getName()).append("\n");
        result.append("Description: ").append(skill.getDescription()).append("\n");
        result.append("Source: ").append(skill.getSource()).append("\n\n");
        result.append("Content:\n");
        result.append("---\n");
        result.append(skill.getSkillContent());
        result.append("\n---\n");
        return result.toString();
    }

    /**
     * 构建普通资源内容的响应，包含加载确认和资源原文。
     *
     * @param skillId         技能 ID
     * @param path            资源路径
     * @param resourceContent 资源内容
     * @return 格式化后的资源响应
     */
    private String buildResourceResponse(String skillId, String path, String resourceContent) {
        StringBuilder result = new StringBuilder();
        result.append("Successfully loaded resource from skill: ").append(skillId).append("\n");
        result.append("Resource path: ").append(path).append("\n\n");
        result.append("Content:\n");
        result.append("---\n");
        result.append(resourceContent);
        result.append("\n---\n");
        return result.toString();
    }

    /**
     * 构建资源未找到时的错误消息，包含 SKILL.md 和所有可用资源路径的列表。
     *
     * <p>返回的消息始终将 "SKILL.md" 列为第一个可用资源，引导 LLM 优先加载技能文档。
     *
     * @param skillId  技能 ID
     * @param path     请求的未找到的路径
     * @param resources 当前技能的所有资源映射
     * @return 包含可用资源列表的格式化错误消息
     */
    private String buildResourceNotFoundMessage(
            String skillId, String path, Map<String, String> resources) {
        StringBuilder message = new StringBuilder();
        message.append("Resource not found: '")
                .append(path)
                .append("' in skill '")
                .append(skillId)
                .append("'.\n\n");

        // Build available resources list with SKILL.md as the first item
        List<String> resourcePaths = new ArrayList<>();
        resourcePaths.add("SKILL.md"); // Always add SKILL.md as the first resource

        if (resources != null && !resources.isEmpty()) {
            resourcePaths.addAll(resources.keySet());
        }

        message.append("Available resources:\n");
        for (int i = 0; i < resourcePaths.size(); i++) {
            message.append(i + 1).append(". ").append(resourcePaths.get(i)).append("\n");
        }

        return message.toString();
    }

    /**
     * 验证技能是否已注册，并返回技能实例。
     *
     * <p>执行双重检查：首先确认注册表中存在该技能 ID，然后验证对应的技能实例不为 null。
     * 如果验证通过，返回技能实例供后续资源加载使用。
     *
     * @param skillId 技能的唯一标识符
     * @return 已注册的技能实例
     * @throws IllegalArgumentException 如果技能未注册
     * @throws IllegalStateException 如果技能在验证后无法加载（内部错误）
     */
    private AgentSkill validateSkillExists(String skillId) {
        if (!skillRegistry.exists(skillId)) {
            throw new IllegalArgumentException(
                    String.format("Skill not found: '%s'. Please check the skill ID.", skillId));
        }
        // Get skill
        AgentSkill skill = skillRegistry.getSkill(skillId);
        if (skill == null) {
            throw new IllegalStateException(
                    String.format(
                            "Failed to load skill '%s' after validation. This is an internal"
                                    + " error.",
                            skillId));
        }
        return skill;
    }

    private void activateSkill(String skillId) {
        skillRegistry.setSkillActive(skillId, true);
        logger.info("Activated skill: {}", skillId);

        String toolsGroupName = skillRegistry.getRegisteredSkill(skillId).getToolsGroupName();
        if (toolkit.getToolGroup(toolsGroupName) != null) {
            toolkit.updateToolGroups(List.of(toolsGroupName), true);
            logger.info(
                    "Activated skill tool group: {} and its tools: {}",
                    toolsGroupName,
                    toolkit.getToolGroup(toolsGroupName).getTools());
        }
    }
}
