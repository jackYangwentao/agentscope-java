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
package io.agentscope.examples.quickstart;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.coding.ShellCommandTool;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Set;

/**
 * AgentSkillExample —— 演示 Agent 使用 skill-creator 技能创建新技能的完整工作流。
 *
 * <p>完整流程包括：
 * <ul>
 *   <li>通过 FileSystemSkillRepository 从资源目录加载技能</li>
 *   <li>启用代码执行工具用于编写新技能文件</li>
 *   <li>运行示例提示词，在磁盘上创建新的技能文件</li>
 * </ul>
 */
public class AgentSkillExample {

    private static final String SKILL_NAME = "skill-creator";
    private static final String RESOURCES_DIR =
            "agentscope-examples/quickstart/src/main/resources/skills";
    private static final String OUTPUT_DIR = "agentscope-examples/quickstart/target/skill-output";

    public static void main(String[] args) throws Exception {
        ExampleUtils.printWelcome(
                "Agent 技能示例 —— Skill Creator",
                "本示例演示 ReActAgent 使用 skill-creator 技能。\n"
                        + "Agent 将：\n"
                        + "  - 从资源目录加载 skill-creator 技能\n"
                        + "  - 使用文件工具创建新技能\n"
                        + "  - 在目标文件夹下编写 SKILL.md 和参考文件");

        String apiKey = ExampleUtils.getDashScopeApiKey();

        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);

        AgentSkill skillCreator = loadSkillCreatorSkill();
        skillBox.registration().skill(skillCreator).apply();

        Path outputDir = resolvePath(OUTPUT_DIR);
        Scanner scanner = new Scanner(System.in);
        ShellCommandTool shellCommandTool =
                new ShellCommandTool(
                        Set.of("python", "ls", "cat"),
                        cmd -> {
                            System.out.println("Enter y/n to approve or deny execution:");
                            System.out.println(cmd);
                            System.out.println();
                            String response = scanner.nextLine();
                            return response.equalsIgnoreCase("y");
                        });

        skillBox.codeExecution()
                .workDir(outputDir.toString())
                .withShell(shellCommandTool)
                .withRead()
                .withWrite()
                .enable();

        ReActAgent agent =
                ReActAgent.builder()
                        .name("SkillCreator")
                        .sysPrompt(buildSystemPrompt(outputDir))
                        .model(
                                OllamaChatModel.builder()
                                        .modelName("llama3.2")
                                        .baseUrl("http://localhost:11434")
                                        .build())
                        .toolkit(toolkit)
                        .skillBox(skillBox)
                        .memory(new InMemoryMemory())
                        .build();

        ExampleUtils.startChat(agent);

        scanner.close();
    }

    private static AgentSkill loadSkillCreatorSkill() {
        Path resourcesDir = resolvePath(RESOURCES_DIR);
        FileSystemSkillRepository repository = new FileSystemSkillRepository(resourcesDir, false);
        return repository.getSkill(SKILL_NAME);
    }

    private static Path resolvePath(String relativePath) {
        return Paths.get(relativePath).toAbsolutePath().normalize();
    }

    private static String buildSystemPrompt(Path outputDir) {
        return """
        You are a skill creation assistant. Use the skill-creator skill when asked to create or
        update a skill. Prefer concise SKILL.md content and put detailed guidance in references.

        File tools are available. Write new skills under this output directory:
        %s

        Use write_text_file to create files and include a valid YAML frontmatter with name and
        description.
        """
                .formatted(outputDir.toString());
    }
}
