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

import io.agentscope.core.skill.util.SkillFileSystemHelper;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ExtendedModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.coding.CommandValidator;
import io.agentscope.core.tool.coding.ShellCommandTool;
import io.agentscope.core.tool.file.ReadFileTool;
import io.agentscope.core.tool.file.WriteFileTool;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 技能箱，作为技能管理系统的核心入口，负责管理智能体技能的注册、加载、激活、
 * 工具化以及代码执行环境的配置。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>技能注册与管理</b>——注册、查询、移除技能，管理技能的激活/停用状态</li>
 *   <li><b>技能工具化</b>——将技能注册为工具组，使 LLM 可以通过工具调用加载和使用技能</li>
 *   <li><b>提示词生成</b>——通过 {@link AgentSkillPromptProvider} 为 LLM 生成可用技能目录</li>
 *   <li><b>代码执行</b>——通过 {@link CodeExecutionBuilder} 配置 shell/read/write 等代码执行工具</li>
 *   <li><b>资源上传</b>——将技能引用的资源文件写入磁盘供脚本执行使用</li>
 *   <li><b>状态管理</b>——实现 {@link StateModule} 接口，支持状态快照和恢复</li>
 * </ul>
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>通过 {@link #registerSkill(AgentSkill)} 或 {@link #registration()} 注册技能</li>
 *   <li>通过 {@link #registerSkillLoadTool()} 注册技能访问工具（load_skill_through_path）</li>
 *   <li>每次智能体调用开始时调用 {@link #deactivateAllSkills()} 重置状态</li>
 *   <li>LLM 通过 {@code load_skill_through_path} 加载技能时自动激活并启用其工具组</li>
 *   <li>通过 {@link #syncToolGroupStates()} 同步技能激活状态与工具组状态</li>
 * </ol>
 *
 * <p><b>线程安全：</b>该类的所有公开方法均为线程安全的，内部使用并发集合和同步机制。
 *
 * @see AgentSkill
 * @see SkillRegistry
 * @see SkillToolFactory
 * @see AgentSkillPromptProvider
 */
public class SkillBox implements StateModule {
    private static final Logger logger = LoggerFactory.getLogger(SkillBox.class);
    private static final String BASE64_PREFIX = "base64:";

    private final SkillRegistry skillRegistry = new SkillRegistry();
    private final AgentSkillPromptProvider skillPromptProvider;
    private final SkillToolFactory skillToolFactory;
    private Toolkit toolkit;
    private Path workDir;
    private Path uploadDir;
    private SkillFileFilter fileFilter;
    private boolean autoUploadSkill = true;

    private static final ConcurrentHashMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    public SkillBox(Toolkit toolkit) {
        this(toolkit, null);
    }

    /**
     * 创建一个 SkillBox 实例，绑定指定的工具包和自定义技能提示指令。
     *
     * @param toolkit     要绑定的工具包（不可为 null）
     * @param instruction 自定义指令头部内容（null 或空白使用默认指令模板）
     */
    public SkillBox(Toolkit toolkit, String instruction) {
        this.skillPromptProvider = new AgentSkillPromptProvider(skillRegistry, instruction);
        this.skillToolFactory = new SkillToolFactory(skillRegistry, toolkit);
        this.toolkit = toolkit;
    }

    /**
     * 获取已注册技能的系统提示词。
     *
     * <p>该提示词提供了当前可用技能的目录信息，智能体可以在执行过程中
     * 动态加载和使用这些技能。
     *
     * @return 技能系统提示词字符串，如果没有已注册技能则返回空字符串
     */
    public String getSkillPrompt() {
        return skillPromptProvider.getSkillSystemPrompt();
    }

    /**
     * 控制技能提示词中暴露所有元数据字段还是仅暴露核心字段。
     *
     * <p>当禁用时，技能提示词中仅包含 {@code name}、{@code description}
     * 和 {@code skill-id} 三个核心字段。这可以减少提示词长度，降低 LLM 的 Token 消耗。
     *
     * @param exposeAllMetadata {@code true} 暴露所有元数据字段，
     *                          {@code false} 仅暴露三个核心字段
     */
    public void setExposeAllSkillMetadata(boolean exposeAllMetadata) {
        skillPromptProvider.setExposeAllMetadata(exposeAllMetadata);
    }

    /**
     * 创建一个流式的技能注册构建器，支持可选配置（关联工具、MCP 客户端、子智能体等）。
     *
     * <p>使用示例：
     * <pre>{@code
     * // 只注册技能
     * skillBox.registration()
     *     .skill(skill)
     *     .apply();
     *
     * // 注册技能并绑定工具对象
     * skillBox.registration()
     *     .skill(skill)
     *     .tool(toolObject)
     *     .apply();
     *
     * // 注册技能并绑定 MCP 客户端
     * skillBox.registration()
     *     .skill(skill)
     *     .mcpClient(client)
     *     .apply();
     *
     * // 注册技能并绑定子智能体
     * skillBox.registration()
     *     .skill(skill)
     *     .subAgent(provider)
     *     .apply();
     * }</pre>
     *
     * @return 新的 {@link SkillRegistration} 构建器实例
     */
    public SkillRegistration registration() {
        return new SkillRegistration(this);
    }

    /**
     * 将工具包绑定到技能箱及其内部的技能工具工厂。
     *
     * <p>由于 {@code ReActAgent} 使用 {@code Toolkit} 的深拷贝，智能体创建后
     * 需要调用此方法重新绑定，以确保技能工具工厂中的工具包引用指向正确的实例。
     * 同时也会触发工具组状态的同步。
     *
     * @param toolkit 要绑定的工具包实例（不可为 null）
     * @throws IllegalArgumentException 如果 toolkit 为 null
     */
    public void bindToolkit(Toolkit toolkit) {
        if (toolkit == null) {
            throw new IllegalArgumentException("Toolkit cannot be null");
        }
        this.toolkit = toolkit;
        // ReActAgent uses a deep copy of Toolkit, so we need to rebind it here
        this.skillToolFactory.bindToolkit(toolkit);
    }

    /**
     * 根据技能的激活状态同步工具组的状态。
     *
     * <p>遍历所有已注册技能，将已激活技能对应的工具组启用，已停用技能对应的工具组禁用。
     * 此方法确保 {@code Toolkit} 中的工具组状态与 {@code SkillRegistry} 中的技能激活状态保持一致。
     * 对于尚未在工具包中创建工具组的技能，会自动跳过。
     */
    public void syncToolGroupStates() {
        if (toolkit == null) {
            return;
        }
        List<String> inactiveSkillToolGroups = new ArrayList<>();
        List<String> activeSkillToolGroups = new ArrayList<>();

        // Dynamically update active/inactive tool groups based on skills' states
        for (RegisteredSkill registeredSkill : skillRegistry.getAllRegisteredSkills().values()) {
            if (toolkit.getToolGroup(registeredSkill.getToolsGroupName()) == null) {
                continue; // Skip uncreated skill tools
            }
            if (!registeredSkill.isActive()) {
                inactiveSkillToolGroups.add(registeredSkill.getToolsGroupName());
                continue; // Skip inactive skill's tools, its tools won't be included
            }
            activeSkillToolGroups.add(registeredSkill.getToolsGroupName());
        }
        toolkit.updateToolGroups(inactiveSkillToolGroups, false);
        toolkit.updateToolGroups(activeSkillToolGroups, true);
        logger.debug(
                "Active Skill Tool Groups updated {}, inactive Skill Tool Groups updated {}",
                activeSkillToolGroups,
                inactiveSkillToolGroups);
    }

    /**
     * 检查指定技能是否处于激活状态。
     *
     * <p>激活状态表示该技能当前正在被 LLM 使用。当 LLM 通过 {@code load_skill_through_path}
     * 工具加载技能时，技能会被自动激活。
     *
     * @param skillId 技能 ID
     * @return true 表示技能已激活（被 LLM 使用中），false 表示未激活
     */
    public boolean isSkillActive(String skillId) {
        RegisteredSkill registeredSkill = skillRegistry.getRegisteredSkill(skillId);
        if (registeredSkill == null) {
            return false;
        }
        return registeredSkill.isActive();
    }

    // ==================== Skill Management ====================

    /**
     * 注册一个智能体技能。
     *
     * <p>技能注册后可以通过技能访问工具被 LLM 动态加载。当技能被加载时，
     * 其关联的工具组会被启用，LLM 方可调用其中的工具。
     *
     * <p><b>版本管理：</b>
     * <ul>
     *   <li>首次注册：创建技能的初始版本</li>
     *   <li>同一技能对象再次注册（按引用比较）：不创建新版本，幂等操作</li>
     *   <li>不同的技能对象注册到同一 ID：替换为新版本</li>
     * </ul>
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * AgentSkill mySkill = new AgentSkill("my_skill", "Description", "Content", null);
     *
     * skillBox.registerSkill(mySkill);
     * skillBox.registerSkill(mySkill); // 不执行任何操作（同一引用）
     * }</pre>
     *
     * @param skill 要注册的智能体技能（不可为 null）
     * @throws IllegalArgumentException 如果 skill 为 null
     */
    public void registerSkill(AgentSkill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("AgentSkill cannot be null");
        }

        String skillId = skill.getSkillId();

        // Create registered wrapper
        RegisteredSkill registered = new RegisteredSkill(skillId);

        // Register in skillRegistry
        skillRegistry.registerSkill(skillId, skill, registered);

        logger.info("Registered skill '{}'", skillId);
    }

    /**
     * 获取所有已注册技能的 ID 集合。
     *
     * @return 所有技能 ID 的集合
     */
    public Set<String> getAllSkillIds() {
        return skillRegistry.getSkillIds();
    }

    /**
     * 根据技能 ID 获取技能实例。
     *
     * @param skillId 技能 ID
     * @return 技能实例，如果未找到则返回 null
     * @throws IllegalArgumentException 如果 skillId 为 null
     */
    public AgentSkill getSkill(String skillId) {
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID cannot be null");
        }
        return skillRegistry.getSkill(skillId);
    }

    /**
     * 完全移除一个技能及其所有关联的注册信息。
     *
     * @param skillId 要移除的技能 ID
     * @throws IllegalArgumentException 如果 skillId 为 null
     */
    public void removeSkill(String skillId) {
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID cannot be null");
        }
        skillRegistry.removeSkill(skillId);
        logger.info("Removed skill '{}'", skillId);
    }

    /**
     * 检查指定技能 ID 是否已注册。
     *
     * @param skillId 技能 ID
     * @return true 表示技能已注册，false 表示未注册
     * @throws IllegalArgumentException 如果 skillId 为 null
     */
    public boolean exists(String skillId) {
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID cannot be null");
        }
        return skillRegistry.exists(skillId);
    }

    /**
     * 设置指定技能的激活状态。
     *
     * <p>当技能被设置为非激活时，其关联的工具组将被禁用，LLM 在重新激活前无法访问其工具。
     * 此方法会自动将状态变更同步到绑定的工具包。
     *
     * <p><b>停用警告：</b>
     * 将技能设置为非激活仅会解绑其关联的工具组，不会自动从智能体的记忆（{@code Memory}）
     * 中清除技能的上下文或提示指令。这可能是有风险的操作，因为智能体可能根据其保留的
     * 记忆上下文尝试调用已停用的工具，导致执行失败。要实现完整的理想停用方案，
     * 建议实现自定义钩子以同时解绑工具组和从记忆中清除相关上下文。
     *
     * @param skillId 要修改的技能 ID
     * @param active  true 激活技能，false 停用技能
     * @throws IllegalArgumentException 如果 skillId 为 null 或技能不存在
     */
    public void setSkillActive(String skillId, boolean active) {
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID cannot be null");
        }

        if (!exists(skillId)) {
            throw new IllegalArgumentException("Skill ID does not exist: " + skillId);
        }

        skillRegistry.setSkillActive(skillId, active);

        // sync ToolGroup state
        RegisteredSkill registeredSkill = skillRegistry.getRegisteredSkill(skillId);
        if (registeredSkill != null) {
            String toolGroupName = registeredSkill.getToolsGroupName();
            if (this.toolkit.getToolGroup(toolGroupName) != null) {
                this.toolkit.updateToolGroups(List.of(toolGroupName), active);
            }
        }

        logger.debug("Skill '{}' active state set to {}", skillId, active);
    }

    /**
     * 停用所有已注册的技能。
     *
     * <p>将所有技能的激活状态设置为 false，使其关联的工具组对智能体不可用，
     * 直到 LLM 通过技能访问工具再次加载这些技能。
     *
     * <p>此方法通常在每次智能体调用开始时调用，以确保干净的初始状态，
     * 防止上次调用的技能激活状态影响本次执行。
     */
    public void deactivateAllSkills() {
        skillRegistry.setAllSkillsActive(false);
        logger.debug("Deactivated all skills");
    }

    /**
     * 技能注册的流式构建器，支持将技能与工具、MCP 客户端、子智能体等关联注册。
     *
     * <p>该构建器提供了一种清晰、类型安全的方式来注册技能及其可选配置，
     * 避免了方法数量膨胀。通过链式调用可以组合多个配置选项。
     *
     * <p><b>支持的可选配置：</b>
     * <ul>
     *   <li>{@link #tool(Object)} —— 注册包含 {@code @Tool} 注解方法的工具对象</li>
     *   <li>{@link #agentTool(AgentTool)} —— 注册 AgentTool 实例</li>
     *   <li>{@link #mcpClient(McpClientWrapper)} —— 注册 MCP 客户端</li>
     *   <li>{@link #subAgent(SubAgentProvider, SubAgentConfig)} —— 注册子智能体</li>
     *   <li>{@link #presetParameters(Map)} —— 预设参数自动注入</li>
     *   <li>{@link #extendedModel(ExtendedModel)} —— 动态 schema 扩展</li>
     * </ul>
     */
    public static class SkillRegistration {
        private final SkillBox skillBox;
        private Toolkit toolkit;
        private AgentSkill skill;
        private Object toolObject;
        private AgentTool agentTool;
        private McpClientWrapper mcpClientWrapper;
        private SubAgentProvider<?> subAgentProvider;
        private SubAgentConfig subAgentConfig;
        private Map<String, Map<String, Object>> presetParameters;
        private ExtendedModel extendedModel;
        private List<String> enableTools;
        private List<String> disableTools;

        public SkillRegistration(SkillBox skillBox) {
            this.skillBox = skillBox;
        }

        /**
         * 设置要注册的技能。
         *
         * @param skill 要注册的技能（不可为 null）
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration skill(AgentSkill skill) {
            this.skill = skill;
            return this;
        }

        public SkillRegistration toolkit(Toolkit toolkit) {
            this.toolkit = toolkit;
            return this;
        }

        /**
         * 设置要注册的工具对象（自动扫描 {@code @Tool} 注解方法）。
         *
         * @param toolObject 包含 {@code @Tool} 注解方法的对象
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration tool(Object toolObject) {
            this.toolObject = toolObject;
            return this;
        }

        /**
         * 设置要注册的 AgentTool 实例。
         *
         * @param agentTool AgentTool 实例
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration agentTool(AgentTool agentTool) {
            this.agentTool = agentTool;
            return this;
        }

        /**
         * 设置要注册的 MCP 客户端。
         *
         * @param mcpClientWrapper MCP 客户端包装器
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration mcpClient(McpClientWrapper mcpClientWrapper) {
            this.mcpClientWrapper = mcpClientWrapper;
            return this;
        }

        /**
         * 使用默认配置注册子智能体作为工具。
         *
         * <p>工具名称和描述从智能体的属性派生。默认使用单个字符串参数 "task"。
         *
         * <p>示例：
         * <pre>{@code
         * toolkit.registration()
         *     .subAgent(() -> ReActAgent.builder()
         *         .name("ResearchAgent")
         *         .model(model)
         *         .build())
         *     .apply();
         * }</pre>
         *
         * @param provider 智能体实例工厂（每次调用时创建新实例）
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration subAgent(SubAgentProvider<?> provider) {
            return subAgent(provider, null);
        }

        /**
         * Register a sub-agent as a tool with custom configuration.
         *
         * <p>Sub-agents support multi-turn conversation with session-based state management. The
         * tool exposes two parameters: {@code message} (required) and {@code session_id} (optional,
         * for continuing existing conversations).
         *
         * <p>Example with custom tool name and description:
         *
         * <pre>{@code
         * toolkit.registration()
         *     .subAgent(
         *         () -> ReActAgent.builder().name("Expert").model(model).build(),
         *         SubAgentConfig.builder()
         *             .toolName("ask_expert")
         *             .description("Ask the domain expert a question")
         *             .build())
         *     .apply();
         * }</pre>
         *
         * <p>Example with persistent session for cross-process conversations:
         *
         * <pre>{@code
         * toolkit.registration()
         *     .subAgent(
         *         () -> ReActAgent.builder().name("Assistant").model(model).build(),
         *         SubAgentConfig.builder()
         *             .session(new JsonSession(Path.of("sessions")))
         *             .forwardEvents(true)
         *             .build())
         *     .apply();
         * }</pre>
         *
         * @param provider 智能体实例工厂（每次调用时创建新实例）
         * @param config 子智能体工具配置，为 null 时使用默认配置
         *     （工具名称从智能体名称派生，使用 InMemorySession 管理状态，事件自动转发）
         * @return 当前构建器实例（用于链式调用）
         * @see SubAgentConfig
         * @see SubAgentConfig#defaults()
         */
        public SkillRegistration subAgent(SubAgentProvider<?> provider, SubAgentConfig config) {
            if (this.toolObject != null
                    || this.agentTool != null
                    || this.mcpClientWrapper != null) {
                throw new IllegalStateException(
                        "Cannot set multiple registration types. Use only one of: tool(),"
                                + " agentTool(), mcpClient(), or subAgent().");
            }
            this.subAgentProvider = provider;
            this.subAgentConfig = config;
            return this;
        }

        /**
         * 设置 MCP 客户端中要启用的工具列表。
         *
         * <p>仅在使用 {@link #mcpClient(McpClientWrapper)} 时生效。
         * 如果未指定，默认启用所有工具。
         *
         * @param enableTools 要启用的工具名称列表
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration enableTools(List<String> enableTools) {
            this.enableTools = enableTools;
            return this;
        }

        /**
         * 设置 MCP 客户端中要禁用的工具列表。
         *
         * <p>仅在使用 {@link #mcpClient(McpClientWrapper)} 时生效。
         *
         * @param disableTools 要禁用的工具名称列表
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration disableTools(List<String> disableTools) {
            this.disableTools = disableTools;
            return this;
        }

        /**
         * 设置预设参数，这些参数将在工具执行时自动注入。
         *
         * <p>预设参数不会暴露在 JSON schema 中，适用于固定值的内部参数。
         *
         * <p>映射结构：工具名称为键，参数映射为值。
         * <pre>{@code
         * Map.of(
         *     "toolName1", Map.of("param1", "value1", "param2", "value2"),
         *     "toolName2", Map.of("param1", "value3")
         * )
         * }</pre>
         *
         * @param presetParameters 工具名称到其预设参数的映射
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration presetParameters(
                Map<String, Map<String, Object>> presetParameters) {
            this.presetParameters = presetParameters;
            return this;
        }

        /**
         * 设置扩展模型用于动态 schema 扩展。
         *
         * <p>扩展模型允许在运行时动态添加或修改工具参数的 JSON schema，
         * 适用于需要根据上下文动态调整参数结构的场景。
         *
         * @param extendedModel 扩展模型实例
         * @return 当前构建器实例（用于链式调用）
         */
        public SkillRegistration extendedModel(ExtendedModel extendedModel) {
            this.extendedModel = extendedModel;
            return this;
        }

        /**
         * 应用所有已配置的选项，完成技能注册。
         *
         * <p>此方法将执行以下操作：
         * <ul>
         *   <li>将技能注册到 {@code SkillBox} 中</li>
         *   <li>如果配置了工具对象、AgentTool、MCP 客户端或子智能体，创建一个工具组并注册相应工具</li>
         *   <li>应用预设参数和扩展模型配置</li>
         * </ul>
         *
         * @throws IllegalStateException 如果未调用 {@code skill()} 设置技能，
         *     或者需要 toolkit() 但未设置
         */
        public void apply() {
            if (skill == null) {
                throw new IllegalStateException("Must call skill() before apply()");
            }
            skillBox.registerSkill(skill);

            if (toolObject != null
                    || agentTool != null
                    || mcpClientWrapper != null
                    || subAgentProvider != null) {
                if (toolkit == null && (toolkit = skillBox.toolkit) == null) {
                    throw new IllegalStateException(
                            "Must bind toolkit or call toolkit() before apply()");
                }
                String skillToolGroup = skill.getSkillId() + "_skill_tools";
                if (toolkit.getToolGroup(skillToolGroup) == null) {
                    toolkit.createToolGroup(skillToolGroup, skillToolGroup, false);
                }
                toolkit.registration()
                        .group(skillToolGroup)
                        .presetParameters(presetParameters)
                        .extendedModel(extendedModel)
                        .enableTools(enableTools)
                        .disableTools(disableTools)
                        .agentTool(agentTool)
                        .tool(toolObject)
                        .mcpClient(mcpClientWrapper)
                        .subAgent(subAgentProvider, subAgentConfig)
                        .apply();
            }
        }
    }

    // ==================== Skill Build-In Tools ====================

    /**
     * 向绑定的 toolkit 注册技能访问工具。
     *
     * <p>此方法注册以下工具：
     * <ul>
     *   <li>{@code load_skill_through_path} —— 加载技能资源或 SKILL.md 内容。
     *       当资源未找到时，自动返回可用资源列表，其中 SKILL.md 为首项。</li>
     * </ul>
     *
     * @throws IllegalArgumentException 如果 toolkit 为 null
     */
    public void registerSkillLoadTool() {
        if (toolkit == null) {
            throw new IllegalArgumentException("Toolkit cannot be null");
        }

        if (toolkit.getToolGroup("skill-build-in-tools") == null) {
            toolkit.createToolGroup(
                    "skill-build-in-tools",
                    "skill build-in tools, could contain(load_skill_through_path)");
        }

        toolkit.registration()
                .agentTool(skillToolFactory.createSkillAccessToolAgentTool())
                .group("skill-build-in-tools")
                .apply();

        logger.info("Registered skill load tools to toolkit");
    }

    // ==================== Code Execution ====================

    /**
     * 创建用于配置代码执行的流式构建器。
     *
     * <p>这是为技能启用代码执行能力的推荐方式。
     * 构建器允许选择性地启用不同工具并对 ShellCommandTool 进行自定义。
     *
     * <p>使用示例：
     * <pre>{@code
     * // 简单用法——使用默认配置启用所有工具
     * skillBox.codeExecution()
     *     .withShell()
     *     .withRead()
     *     .withWrite()
     *     .enable();
     *
     * // 自定义 shell 工具并添加审批回调
     * ShellCommandTool customShell = new ShellCommandTool(
     *     null,  // baseDir 将被覆盖
     *     Set.of("python3", "node", "npm"),
     *     command -> askUserApproval(command)
     * );
     *
     * skillBox.codeExecution()
     *     .workDir("/path/to/workdir")
     *     .withShell(customShell)  // 克隆并设置 workDir
     *     .withRead()
     *     .withWrite()
     *     .enable();
     *
     * // 仅启用读取和写入工具
     * skillBox.codeExecution()
     *     .withRead()
     *     .withWrite()
     *     .enable();
     * }</pre>
     *
     * @return 一个新的用于配置的 CodeExecutionBuilder 实例
     */
    public CodeExecutionBuilder codeExecution() {
        return new CodeExecutionBuilder(this);
    }

    /**
     * 设置是否自动上传技能文件。
     *
     * @param autoUploadSkill {@code true} 表示自动上传技能文件
     */
    public void setAutoUploadSkill(boolean autoUploadSkill) {
        this.autoUploadSkill = autoUploadSkill;
    }

    /**
     * Checks whether skill files are automatically uploaded.
     *
     * @return true if skill files are automatically uploaded
     */
    public boolean isAutoUploadSkill() {
        return autoUploadSkill;
    }

    /**
     * 获取代码执行的工作目录。
     *
     * @return 工作目录路径，如果使用临时目录则返回 null
     */
    public Path getCodeExecutionWorkDir() {
        return workDir;
    }

    /**
     * 获取技能文件的上传目录。
     *
     * @return 上传目录路径，如果未配置则返回 null
     */
    public Path getUploadDir() {
        return uploadDir;
    }

    /**
     * 确保工作目录存在，如果不存在则创建。
     *
     * <p>如果当前未设置工作目录，将创建一个临时目录并注册 JVM 关闭钩子进行清理。
     *
     * @return 工作目录路径
     * @throws RuntimeException 如果目录创建失败
     */
    private Path ensureWorkDirExists() {
        if (this.workDir == null) {
            // Create temporary directory
            try {
                this.workDir = Files.createTempDirectory("agentscope-code-execution-");

                SkillFileSystemHelper.registerTempDirectoryCleanup(workDir);

                logger.info("Created temporary working directory: {}", workDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temporary working directory", e);
            }
        } else {
            // Create directory if it doesn't exist
            if (!Files.exists(workDir)) {
                try {
                    Files.createDirectories(workDir);
                    logger.info("Created working directory: {}", workDir);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create working directory", e);
                }
            }
        }

        return this.workDir;
    }

    /**
     * 确保上传目录存在，如果不存在则创建。
     *
     * <p>如果未配置上传目录但工作目录存在，默认使用 {@code workDir/skills} 作为上传目录。
     *
     * @return 上传目录路径
     */
    private Path ensureUploadDirExists() {
        if (uploadDir == null) {
            Path resolvedWorkDir = ensureWorkDirExists();
            uploadDir = resolvedWorkDir.resolve("skills");
        }

        if (!Files.exists(uploadDir)) {
            try {
                Files.createDirectories(uploadDir);
                logger.info("Created upload directory: {}", uploadDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create upload directory", e);
            }
        }

        skillPromptProvider.setUploadDir(uploadDir);
        return uploadDir;
    }

    /**
     * 使用配置的文件过滤器将技能文件上传到上传目录。
     *
     * <p>上传目录解析顺序：
     * <ul>
     *   <li>如果配置了 {@code uploadDir}，直接使用该路径。</li>
     *   <li>否则，使用 {@code workDir/skills}（{@code workDir} 可能是临时目录）。</li>
     * </ul>
     *
     * <p>如果目标文件已存在，将被覆盖写入。
     *
     */
    public void uploadSkillFiles() {
        Path targetDir = ensureUploadDirExists();
        SkillFileFilter filter = fileFilter != null ? fileFilter : SkillFileFilter.acceptAll();
        int fileCount = 0;

        for (String skillId : getAllSkillIds()) {
            AgentSkill skill = getSkill(skillId);
            Set<String> resourcePaths = skill.getResourcePaths();

            if (resourcePaths.isEmpty()) {
                continue;
            }

            Path skillDir = targetDir.resolve(skillId);

            for (String resourcePath : resourcePaths) {
                if (!filter.accept(resourcePath)) {
                    continue;
                }

                String content = skill.getResource(resourcePath);
                if (content == null) {
                    logger.warn("Resource not found: {} in skill {}", resourcePath, skillId);
                    continue;
                }

                Path targetPath = skillDir.resolve(resourcePath).normalize();

                // Security check: Prevent path traversal attacks
                if (!targetPath.startsWith(skillDir)) {
                    logger.warn("Skipping file with invalid path: {}", resourcePath);
                    continue;
                }

                try {
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }

                    Object lock =
                            FILE_LOCKS.computeIfAbsent(targetPath.toString(), k -> new Object());
                    synchronized (lock) {
                        if (content.startsWith(BASE64_PREFIX)) {
                            String encoded = content.substring(BASE64_PREFIX.length());
                            byte[] decoded = Base64.getDecoder().decode(encoded);
                            Files.write(targetPath, decoded);
                        } else {
                            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
                        }
                    }

                    logger.debug("Uploaded file: {}", targetPath);
                    fileCount++;
                } catch (IOException | IllegalArgumentException e) {
                    logger.error("Failed to upload file {}: {}", resourcePath, e.getMessage());
                }
            }
        }

        logger.info("Uploaded {} skill files to: {}", fileCount, targetDir);
    }

    private static class DefaultSkillFileFilter implements SkillFileFilter {
        private final Set<String> includeFolders;
        private final Set<String> includeExtensions;

        private DefaultSkillFileFilter(Set<String> includeFolders, Set<String> includeExtensions) {
            this.includeFolders = includeFolders != null ? includeFolders : Set.of();
            this.includeExtensions = includeExtensions != null ? includeExtensions : Set.of();
        }

        @Override
        public boolean accept(String resourcePath) {
            if (resourcePath == null || resourcePath.isBlank()) {
                return false;
            }

            String normalizedPath = resourcePath.replace("\\", "/");

            if (!includeFolders.isEmpty()) {
                for (String folder : includeFolders) {
                    if (normalizedPath.startsWith(folder)) {
                        return true;
                    }
                }
            }

            if (!includeExtensions.isEmpty()) {
                for (String extension : includeExtensions) {
                    if (normalizedPath.endsWith(extension)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    // ==================== Code Execution Builder ====================

    /**
     * Fluent builder for configuring code execution with custom options.
     *
     * <p>This builder provides a flexible way to enable code execution capabilities
     * with selective tool enabling and custom ShellCommandTool configuration.
     *
     * <p>Key features:
     * <ul>
     *   <li>Selective tool enabling: choose which tools (shell/read/write) to enable</li>
     *   <li>Custom ShellCommandTool: provide your own tool with custom security policies</li>
     *   <li>WorkDir enforcement: all tools use the same working directory</li>
     *   <li>Tool cloning: custom ShellCommandTool is cloned with workDir override</li>
     * </ul>
     */
    public static class CodeExecutionBuilder {
        private static final Set<String> DEFAULT_INCLUDE_FOLDERS = Set.of("scripts/", "assets/");
        private static final Set<String> DEFAULT_INCLUDE_EXTENSIONS = Set.of(".py", ".js", ".sh");

        private final SkillBox skillBox;
        private String workDir;
        private String uploadDir;
        private SkillFileFilter customFilter;
        private Set<String> includeFolders;
        private Set<String> includeExtensions;
        private ShellCommandTool customShellTool;
        private boolean withShellCalled = false;
        private boolean enableRead = false;
        private boolean enableWrite = false;
        private String codeExecutionInstruction;

        CodeExecutionBuilder(SkillBox skillBox) {
            this.skillBox = skillBox;
        }

        /**
         * 设置代码执行的工作目录。
         *
         * <p>所有代码执行工具（shell、read、write）都将使用此目录作为基础路径。
         * 如果未设置，当文件上传时将创建一个临时目录。
         *
         * @param workDir 工作目录路径（null 或空字符串表示使用临时目录）
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder workDir(String workDir) {
            this.workDir = workDir;
            return this;
        }

        /**
         * 设置技能文件的上传目录。
         *
         * <p>如果未设置，上传目录默认为 {@code workDir/skills}。
         *
         * @param uploadDir 上传目录路径
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder uploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
            return this;
        }

        /**
         * 设置技能文件上传的自定义文件过滤器。
         *
         * <p>注意：{@code fileFilter()} 与 {@code includeFolders()}/{@code includeExtensions()}
         * 互斥，同时使用将导致 {@link #enable()} 抛出异常。
         *
         * @param filter 自定义文件过滤器
         * @return 当前构建器实例（用于链式调用）
         * @throws IllegalArgumentException 如果 filter 为 null
         */
        public CodeExecutionBuilder fileFilter(SkillFileFilter filter) {
            if (filter == null) {
                throw new IllegalArgumentException("SkillFileFilter cannot be null");
            }
            this.customFilter = filter;
            return this;
        }

        /**
         * 设置上传时要包含的文件夹。
         *
         * <p>仅匹配指定文件夹中的文件，与 {@link #includeExtensions(Set)} 组合使用。
         *
         * @param folders 要包含的文件夹路径集合
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder includeFolders(Set<String> folders) {
            this.includeFolders = folders;
            return this;
        }

        /**
         * 设置上传时要包含的文件扩展名。
         *
         * <p>例如 {@code Set.of(".py", ".js", ".txt")}，与 {@link #includeFolders(Set)} 组合使用。
         *
         * @param extensions 要包含的文件扩展名集合
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder includeExtensions(Set<String> extensions) {
            this.includeExtensions = extensions;
            return this;
        }

        /**
         * 使用默认配置启用 shell 命令执行。
         *
         * <p>默认配置：
         * <ul>
         *   <li>允许的命令：python, python3, node, nodejs</li>
         *   <li>无审批回调（不拦截任何命令执行）</li>
         *   <li>平台相关的命令验证器（Unix 或 Windows）</li>
         * </ul>
         *
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder withShell() {
            this.withShellCalled = true;
            this.customShellTool = null;
            return this;
        }

        /**
         * 使用自定义的 ShellCommandTool 启用 shell 命令执行。
         *
         * <p>提供的工具将被克隆，行为如下：
         * <ul>
         *   <li>{@code allowedCommands}：从源工具复制</li>
         *   <li>{@code approvalCallback}：从源工具复制</li>
         *   <li>{@code commandValidator}：从源工具复制</li>
         *   <li>{@code baseDir}：<b>被覆盖</b>为构建器的 {@code workDir}</li>
         * </ul>
         *
         * <p>这确保所有代码执行工具使用相同的工作目录，
         * 同时保留您的自定义安全策略。
         *
         * @param shellTool 要克隆的自定义 ShellCommandTool（不能为 null）
         * @return 当前构建器实例（用于链式调用）
         * @throws IllegalArgumentException 如果 shellTool 为 null
         */
        public CodeExecutionBuilder withShell(ShellCommandTool shellTool) {
            if (shellTool == null) {
                throw new IllegalArgumentException("ShellCommandTool cannot be null");
            }
            this.withShellCalled = true;
            this.customShellTool = shellTool;
            return this;
        }

        /**
         * 启用文件读取能力。
         *
         * <p>使用构建器的 {@code workDir} 作为基础目录注册 {@code ReadFileTool}。
         *
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder withRead() {
            this.enableRead = true;
            return this;
        }

        /**
         * 启用文件写入能力。
         *
         * <p>使用构建器的 {@code workDir} 作为基础目录注册 {@code WriteFileTool}。
         *
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder withWrite() {
            this.enableWrite = true;
            return this;
        }

        /**
         * 设置自定义的代码执行指令模板，用于系统提示词。
         *
         * <p>当启用代码执行时，该指令将追加到技能系统提示词中。
         * 使用 {@code %s} 作为上传目录绝对路径的占位符——每个 {@code %s}
         * 都将被替换为实际的上传目录路径。
         *
         * <p>传入 {@code null} 或空字符串将使用默认指令。
         *
         * @param instruction 自定义代码执行指令模板
         * @return 当前构建器实例（用于链式调用）
         */
        public CodeExecutionBuilder codeExecutionInstruction(String instruction) {
            this.codeExecutionInstruction = instruction;
            return this;
        }

        /**
         * 应用配置并启用代码执行能力。
         *
         * <p>此方法按顺序执行以下操作：
         * <ul>
         *   <li>验证 {@code toolkit} 已绑定</li>
         *   <li>如果已存在代码执行配置，先移除旧的工具组（支持热替换）</li>
         *   <li>创建工作目录和上传目录</li>
         *   <li>创建代码执行工具组 {@code skill_code_execution_tool_group}</li>
         *   <li>注册选中的工具（shell、read、write）</li>
         *   <li>设置技能提示词提供器的代码执行标志和指令</li>
         * </ul>
         *
         * @throws IllegalStateException 如果 toolkit 尚未绑定
         */
        public void enable() {
            if (skillBox.toolkit == null) {
                throw new IllegalStateException("Must bind toolkit before enabling code execution");
            }

            if (customFilter != null && (includeFolders != null || includeExtensions != null)) {
                throw new IllegalStateException(
                        "Cannot use fileFilter() with includeFolders() or includeExtensions()");
            }

            // Handle replacement: remove existing tool group if present
            if (skillBox.toolkit.getToolGroup("skill_code_execution_tool_group") != null) {
                skillBox.toolkit.removeToolGroups(List.of("skill_code_execution_tool_group"));
                logger.info("Replacing existing code execution configuration");
            }

            // Set workDir
            if (workDir == null || workDir.isEmpty()) {
                skillBox.workDir = null;
            } else {
                skillBox.workDir = Paths.get(workDir).toAbsolutePath().normalize();
            }

            // Set uploadDir
            if (uploadDir == null || uploadDir.isBlank()) {
                skillBox.uploadDir =
                        skillBox.workDir != null ? skillBox.workDir.resolve("skills") : null;
            } else {
                skillBox.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            }

            // Set file filter
            if (customFilter != null) {
                skillBox.fileFilter = customFilter;
            } else {
                Set<String> folders =
                        includeFolders != null ? includeFolders : DEFAULT_INCLUDE_FOLDERS;
                Set<String> extensions =
                        includeExtensions != null ? includeExtensions : DEFAULT_INCLUDE_EXTENSIONS;
                skillBox.fileFilter = new DefaultSkillFileFilter(folders, extensions);
            }

            // Create tool group
            skillBox.toolkit.createToolGroup(
                    "skill_code_execution_tool_group", "Code execution tools for skills", true);

            String workDirStr = skillBox.workDir != null ? skillBox.workDir.toString() : null;

            boolean shellEnabled = false;

            // Shell Tool - check if withShell() was called
            if (withShellCalled) {
                ShellCommandTool shellTool;
                if (customShellTool != null) {
                    // Clone custom tool with workDir override
                    shellTool = cloneShellToolWithWorkDir(customShellTool, workDirStr);
                } else {
                    // Create default shell tool
                    shellTool =
                            new ShellCommandTool(
                                    workDirStr,
                                    Set.of("python", "python3", "node", "nodejs"),
                                    null);
                }
                skillBox.toolkit
                        .registration()
                        .agentTool(shellTool)
                        .group("skill_code_execution_tool_group")
                        .apply();
                shellEnabled = true;
            }

            // Read Tool
            if (enableRead) {
                ReadFileTool readTool = new ReadFileTool(workDirStr);
                skillBox.toolkit
                        .registration()
                        .tool(readTool)
                        .group("skill_code_execution_tool_group")
                        .apply();
            }

            // Write Tool
            if (enableWrite) {
                WriteFileTool writeTool = new WriteFileTool(workDirStr);
                skillBox.toolkit
                        .registration()
                        .tool(writeTool)
                        .group("skill_code_execution_tool_group")
                        .apply();
            }

            logger.info(
                    "Code execution enabled with workDir: {}, uploadDir: {}, tools: [shell={},"
                            + " read={}, write={}]",
                    skillBox.workDir != null ? skillBox.workDir : "temporary",
                    skillBox.uploadDir != null ? skillBox.uploadDir : "workDir/skills",
                    shellEnabled,
                    enableRead,
                    enableWrite);

            boolean injectCodeExecutionPrompt = shellEnabled || codeExecutionInstruction != null;
            skillBox.skillPromptProvider.setCodeExecutionEnable(injectCodeExecutionPrompt);
            skillBox.skillPromptProvider.setCodeExecutionInstruction(codeExecutionInstruction);
        }

        /**
         * 克隆 ShellCommandTool 并设置新的基础目录。
         *
         * <p>这确保所有代码执行工具使用相同的工作目录，
         * 同时保留源工具中的自定义安全策略（允许命令、审批回调和命令验证器）。
         *
         * @param source 要克隆的源 ShellCommandTool
         * @param workDir 新的工作目录（可以为 null，表示使用临时目录）
         * @return 具有相同配置但不同 baseDir 的新 ShellCommandTool 实例
         */
        private ShellCommandTool cloneShellToolWithWorkDir(
                ShellCommandTool source, String workDir) {
            // Get configuration from source tool
            Set<String> allowedCommands = source.getAllowedCommands();
            Function<String, Boolean> approvalCallback = source.getApprovalCallback();
            CommandValidator validator = source.getCommandValidator();

            // Create new instance with workDir override
            return new ShellCommandTool(workDir, allowedCommands, approvalCallback, validator);
        }
    }
}
