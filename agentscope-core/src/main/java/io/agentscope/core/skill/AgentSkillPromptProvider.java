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

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 技能提示词提供器，负责为 LLM 生成可用技能的系统提示词。
 *
 * <p>核心功能是将已注册的技能元数据渲染为结构化的 XML 格式提示词，使 LLM
 * 了解可以动态加载和使用的技能。提示词生成遵循以下流程：
 * <ol>
 *   <li>从 {@link SkillRegistry} 获取所有已注册的技能</li>
 *   <li>将每个技能的元数据（名称、描述、自定义字段等）渲染为 {@code <skill>} XML 元素</li>
 *   <li>可选地追加代码执行指令（包括技能根目录路径和脚本执行工作流）</li>
 * </ol>
 *
 * <p><b>元数据暴露控制：</b><br>
 * 通过 {@link #setExposeAllMetadata(boolean)} 可控制提示词中暴露的元数据范围。
 * 完整模式暴露所有元数据字段；精简模式仅暴露 {@code name}、{@code description}
 * 和 {@code skill-id} 三个核心字段。
 *
 * <p><b>代码执行集成：</b><br>
 * 当启用代码执行时（通过 {@link #setCodeExecutionEnable(boolean)}），提示词末尾会追加
 * 代码执行指令模板，其中的 {@code %s} 占位符会被替换为技能文件上传目录的绝对路径，
 * 指导 LLM 如何发现和执行预部署的技能脚本。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * AgentSkillPromptProvider provider = new AgentSkillPromptProvider(registry);
 * String prompt = provider.getSkillSystemPrompt();
 * }</pre>
 *
 * @see SkillRegistry
 * @see SkillBox
 */
public class AgentSkillPromptProvider {
    private static final String INDENT = "  ";
    private static final Pattern XML_TAG_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    private final SkillRegistry skillRegistry;
    private final String instruction;
    private boolean exposeAllMetadata = true;
    private boolean codeExecutionEnabled;
    private String uploadDir;
    private String codeExecutionInstruction;

    public static final String DEFAULT_AGENT_SKILL_INSTRUCTION =
            """
            ## Available Skills

            <usage>
            Skills provide specialized capabilities and domain knowledge. Use them when they match your current task.

            How to use skills:
            - Load skill: load_skill_through_path(skillId="<skill-id>", path="SKILL.md")
            - The skill will be activated and its documentation loaded with detailed instructions
            - Additional resources (scripts, assets, references) can be loaded using the same tool with different paths

            Example:
            1. User asks to analyze data → find a matching skill below (e.g. <skill-id>data-analysis_builtin</skill-id>)
            2. Load it: load_skill_through_path(skillId="data-analysis_builtin", path="SKILL.md")
            3. Follow the instructions returned by the skill

            Metadata is rendered as XML under each <skill> element:
            - scalar metadata becomes a simple child element
            - nested maps become nested XML elements
            - lists become repeated <item> elements
            - <skill-id> is always appended for tool loading
            </usage>

            <available_skills>

            """;

    // Every %s placeholder in the template will be replaced with the uploadDir absolute path
    public static final String DEFAULT_CODE_EXECUTION_INSTRUCTION =
            """

            ## Code Execution

            <code_execution>
            You have access to the execute_shell_command tool. When a task can be accomplished by running\s
            a pre-deployed skill script, you MUST execute it yourself using execute_shell_command rather\s
            than describing or suggesting commands to the user.

            Skills root directory: %s
            Each skill's files are located under a subdirectory named by its <skill-id>:
              %s/<skill-id>/scripts/
              %s/<skill-id>/assets/

            Workflow:
            1. After loading a skill, use ls to explore its directory structure and discover available scripts/assets
            2. Once you find the right script, execute it immediately with its absolute path
            3. If execution fails, diagnose and retry — do not fall back to describing the command

            Rules:
            - Always use absolute paths when executing scripts
            - If a script exists for the task, run it directly — do not rewrite its logic inline
            - If asset/data files exist for the task, read them directly — do not recreate them

            Example:
              # Explore what scripts are available for a skill
              execute_shell_command(command="ls %s/data-analysis_builtin/scripts/")

              # Run an existing script with absolute path
              execute_shell_command(command="python3 %s/data-analysis_builtin/scripts/analyze.py")
            </code_execution>
            """;

    /**
     * 创建技能提示词提供器，使用默认的指令模板。
     *
     * @param registry 技能注册表，包含所有已注册的技能（不可为 null）
     */
    public AgentSkillPromptProvider(SkillRegistry registry) {
        this(registry, null);
    }

    /**
     * 创建技能提示词提供器，并允许自定义指令头部内容。
     *
     * @param registry    技能注册表，包含所有已注册的技能（不可为 null）
     * @param instruction 自定义指令头部内容（null 或空白则使用默认指令模板）
     */
    public AgentSkillPromptProvider(SkillRegistry registry, String instruction) {
        this.skillRegistry = registry;
        this.instruction =
                instruction == null || instruction.isBlank()
                        ? DEFAULT_AGENT_SKILL_INSTRUCTION
                        : instruction;
    }

    /**
     * 获取面向智能体的技能系统提示词。
     *
     * <p>生成包含所有已注册技能信息的系统提示词，格式为 XML 结构化的技能目录。
     * 提示词中包含每个技能的名称、描述、元数据以及技能 ID。
     *
     * <p>如果启用了代码执行且已设置上传目录，还会在提示词末尾追加代码执行指令，
     * 指导 LLM 如何发现和执行技能目录中的脚本。
     *
     * @return 技能系统提示词字符串，如果没有已注册的技能则返回空字符串
     */
    public String getSkillSystemPrompt() {
        if (skillRegistry.getAllRegisteredSkills().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(instruction);

        for (RegisteredSkill registered : skillRegistry.getAllRegisteredSkills().values()) {
            AgentSkill skill = skillRegistry.getSkill(registered.getSkillId());
            appendSkill(sb, skill);
        }

        sb.append("</available_skills>");

        if (codeExecutionEnabled && uploadDir != null) {
            String template =
                    codeExecutionInstruction != null
                            ? codeExecutionInstruction
                            : DEFAULT_CODE_EXECUTION_INSTRUCTION;
            sb.append(template.replace("%s", uploadDir));
        }

        return sb.toString();
    }

    /**
     * 设置是否在技能系统提示词中包含代码执行指令。
     *
     * <p>启用后，提示词末尾会追加代码执行说明，指导 LLM 如何发现和执行
     * 技能上传目录中的预部署脚本。
     *
     * @param codeExecutionEnabled {@code true} 追加代码执行指令，{@code false} 不追加
     */
    public void setCodeExecutionEnable(boolean codeExecutionEnabled) {
        this.codeExecutionEnabled = codeExecutionEnabled;
    }

    /**
     * 设置上传目录路径，其绝对路径将替换代码执行指令模板中的所有 {@code %s} 占位符。
     *
     * <p>当调用 {@link #getSkillSystemPrompt()} 时，代码执行指令模板中的每个 {@code %s}
     * 都会被替换为该路径的绝对路径形式。
     *
     * @param uploadDir 上传目录的路径，或 {@code null} 禁用路径替换
     */
    public void setUploadDir(Path uploadDir) {
        this.uploadDir = uploadDir != null ? uploadDir.toAbsolutePath().toString() : null;
    }

    /**
     * 设置自定义的代码执行指令模板。
     *
     * <p>模板中的每个 {@code %s} 占位符将被替换为 {@code uploadDir} 的绝对路径。
     * 传入 {@code null} 或空白字符串将回退到 {@link #DEFAULT_CODE_EXECUTION_INSTRUCTION}。
     *
     * @param codeExecutionInstruction 自定义模板字符串，或 {@code null}/空白以使用默认模板
     */
    public void setCodeExecutionInstruction(String codeExecutionInstruction) {
        this.codeExecutionInstruction =
                codeExecutionInstruction == null || codeExecutionInstruction.isBlank()
                        ? null
                        : codeExecutionInstruction;
    }

    /**
     * 设置是否向 LLM 暴露技能的所有元数据字段。
     *
     * <p>当禁用时，技能提示词中仅包含 {@code name}、{@code description}
     * 和 {@code skill-id} 三个核心字段。当启用时，技能元数据中的所有键值对
     * 都会被渲染为 XML 元素。
     *
     * @param exposeAllMetadata {@code true} 暴露所有元数据字段，{@code false} 仅暴露核心字段
     */
    public void setExposeAllMetadata(boolean exposeAllMetadata) {
        this.exposeAllMetadata = exposeAllMetadata;
    }

    private void appendSkill(StringBuilder sb, AgentSkill skill) {
        sb.append("<skill>\n");
        for (Map.Entry<String, Object> entry : getPromptMetadata(skill).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            appendXmlNode(sb, entry.getKey(), entry.getValue(), 1);
        }
        appendXmlNode(sb, "skill-id", skill.getSkillId(), 1);
        sb.append("</skill>\n\n");
    }

    private Map<String, Object> getPromptMetadata(AgentSkill skill) {
        if (exposeAllMetadata) {
            return skill.getMetadata();
        }

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", skill.getName());
        metadata.put("description", skill.getDescription());
        return metadata;
    }

    private void appendXmlNode(StringBuilder sb, String key, Object value, int indentLevel) {
        if (value == null) {
            return;
        }

        String indent = INDENT.repeat(indentLevel);
        boolean validTagName = isValidXmlTagName(key);
        String openTag = validTagName ? "<" + key + ">" : "<entry key=\"" + escapeXml(key) + "\">";
        String closeTag = validTagName ? "</" + key + ">" : "</entry>";

        if (isScalarValue(value)) {
            sb.append(indent)
                    .append(openTag)
                    .append(escapeXml(String.valueOf(value)))
                    .append(closeTag)
                    .append("\n");
            return;
        }

        sb.append(indent).append(openTag).append("\n");
        if (value instanceof Map<?, ?> mapValue) {
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                appendXmlNode(
                        sb, String.valueOf(entry.getKey()), entry.getValue(), indentLevel + 1);
            }
        } else if (value instanceof Collection<?> collectionValue) {
            for (Object item : collectionValue) {
                appendXmlNode(sb, "item", item, indentLevel + 1);
            }
        } else {
            sb.append(INDENT.repeat(indentLevel + 1))
                    .append(escapeXml(String.valueOf(value)))
                    .append("\n");
        }
        sb.append(indent).append(closeTag).append("\n");
    }

    private boolean isScalarValue(Object value) {
        return !(value instanceof Map<?, ?>) && !(value instanceof Collection<?>);
    }

    private boolean isValidXmlTagName(String value) {
        return value != null && XML_TAG_NAME_PATTERN.matcher(value).matches();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
