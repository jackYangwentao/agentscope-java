/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.multiagents.skills;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Skills（渐进式披露）SQL 助手的 Spring 配置类。
 *
 * <p>本配置类组装 AgentScope 的技能支持体系，包含三个核心组件：</p>
 * <ul>
 *   <li><b>ClasspathSkillRepository</b> — 从 classpath {@code skills/} 目录加载技能
 *       （每个子目录下包含一个 {@code SKILL.md} 文件，通过 frontmatter 定义技能名称和描述）</li>
 *   <li><b>SkillBox</b> — 持有 {@link Toolkit} 并注册所有已加载的技能；
 *       向 Agent 提供技能相关的 System Prompt，以及 {@code read_skill} / {@code use_skill} 两个工具</li>
 *   <li><b>ReActAgent</b> — 使用 {@link DashScopeChatModel} 作为 LLM，结合 toolkit、skillBox 和
 *       {@link io.agentscope.core.memory.InMemoryMemory} 构建 SQL 查询助手 Agent</li>
 * </ul>
 *
 * <p><b>渐进式披露（Progressive Disclosure）</b>：Agent 的 System Prompt 中仅包含技能的名称和简短描述，
 * 当用户问题涉及特定领域时，Agent 调用 {@code read_skill} 工具按需加载完整的 SKILL.md 内容
 * （表结构、业务逻辑、示例查询）。这种方式避免了将所有技能的详细信息一次性加载到上下文中，
 * 有效节省 Token 并减少干扰信息。</p>
 */
@Configuration
public class SkillsConfig {

    /** Agent 的系统提示词，描述其作为 SQL 查询助手的角色，并提示使用 read_skill 工具按需加载技能详情。 */
    private static final String SYSTEM_PROMPT =
            """
            You are a SQL query assistant that helps users write queries against business databases.
            Use the read_skill tool when you need detailed schema or business logic for a specific domain.
            """;

    /**
     * 创建 ClasspathSkillRepository，从 classpath 的 {@code skills/} 目录加载技能。
     * 每个子目录对应一个技能，目录内必须包含 {@code SKILL.md} 文件。
     *
     * @return ClasspathSkillRepository 实例
     * @throws IOException 如果技能目录不存在或读取失败
     */
    @Bean
    public ClasspathSkillRepository skillRepository() throws IOException {
        return new ClasspathSkillRepository("skills");
    }

    /**
     * 创建空的 Toolkit，用于注册技能工具。
     * SkillBox 将把 {@code read_skill} 和 {@code use_skill} 工具注册到此 Toolkit 中。
     *
     * @return Toolkit 实例
     */
    @Bean
    public Toolkit toolkit() {
        return new Toolkit();
    }

    /**
     * 创建 SkillBox，加载所有技能并注册到 Toolkit。
     *
     * <p>遍历 SkillRepository 中的所有技能，对每个技能调用
     * {@code skillBox.registration().skill(skill).apply()} 完成注册。
     * 注册后，Agent 可以通过 System Prompt 感知可用技能，并通过 skillBox 提供的工具
     * 按需加载和使用技能内容。</p>
     *
     * @param toolkit           Toolkit，用于注册技能工具
     * @param skillRepository   技能仓库，从中获取所有已加载的技能
     * @return 配置完成的 SkillBox 实例
     */
    @Bean
    public SkillBox skillBox(Toolkit toolkit, ClasspathSkillRepository skillRepository) {
        SkillBox skillBox = new SkillBox(toolkit);
        List<AgentSkill> skills = skillRepository.getAllSkills();
        for (AgentSkill skill : skills) {
            skillBox.registration().skill(skill).apply();
        }
        return skillBox;
    }

    /**
     * 创建 SQL 助手 ReActAgent Bean，名称为 {@code "sqlAssistantAgent"}。
     *
     * <p>Agent 的构建包含以下要素：</p>
     * <ul>
     *   <li><b>name</b> — Agent 名称 {@code "sql_assistant"}</li>
     *   <li><b>sysPrompt</b> — 系统提示词，定义 Agent 的角色行为</li>
     *   <li><b>model</b> — DashScope 通义千问模型（{@code qwen-plus}），通过环境变量读取 API Key</li>
     *   <li><b>toolkit</b> — 工具包，包含技能加载和使用的工具</li>
     *   <li><b>skillBox</b> — 技能盒子，管理所有已注册的技能</li>
     *   <li><b>memory</b> — 内存记忆，用于保持对话上下文</li>
     * </ul>
     *
     * @param toolkit  ToolKit，包含技能相关工具
     * @param skillBox SkillBox，管理已注册的技能
     * @return 配置完成的 ReActAgent 实例
     */
    @Bean("sqlAssistantAgent")
    public ReActAgent sqlAssistantAgent(Toolkit toolkit, SkillBox skillBox) {
        String key = System.getenv("AI_DASHSCOPE_API_KEY");
        return ReActAgent.builder()
                .name("sql_assistant")
                .sysPrompt(SYSTEM_PROMPT)
                .model(DashScopeChatModel.builder().apiKey(key).modelName("qwen-plus").build())
                .toolkit(toolkit)
                .skillBox(skillBox)
                .memory(new InMemoryMemory())
                .build();
    }
}
