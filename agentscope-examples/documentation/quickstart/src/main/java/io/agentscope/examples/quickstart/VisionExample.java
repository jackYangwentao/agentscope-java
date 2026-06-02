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
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.quickstart.util.MsgUtils;
import java.util.List;

/**
 * VisionExample —— 演示 Agent 的视觉能力，支持图片分析与描述。
 */
public class VisionExample {

    public static void main(String[] args) throws Exception {
        // 打印欢迎信息
        ExampleUtils.printWelcome(
                "视觉能力示例",
                "本示例演示如何使用 Agent 的视觉能力。\n"
                        + "Agent 可以分析图片并描述其看到的内容。\n"
                        + "\n注意：DashScope 视觉模型推荐使用 Base64 编码的图片以获得最佳兼容性。");

        // 获取 API 密钥
        String apiKey = ExampleUtils.getDashScopeApiKey();

        // 使用视觉模型创建 Agent
        ReActAgent agent =
                ReActAgent.builder()
                        .name("VisionAssistant")
                        .sysPrompt(
                                "You are a helpful AI assistant with vision capabilities. Analyze"
                                        + " images carefully and provide accurate descriptions.")
                        .model(
                                DashScopeChatModel.builder()
                                        .apiKey(apiKey)
                                        .modelName("qwen-vl-max") // Vision model
                                        .stream(true)
                                        .formatter(new DashScopeChatFormatter())
                                        .defaultOptions(GenerateOptions.builder().build())
                                        .build())
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        // Demonstrate vision capability with a simple example
        demonstrateVision(agent);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Interactive Mode");
        System.out.println("=".repeat(80));
        System.out.println("You can now chat with the agent normally.");
        System.out.println("To analyze more images, describe them or ask questions!");
        System.out.println("=".repeat(80) + "\n");

        // Start interactive chat
        ExampleUtils.startChat(agent);
    }

    private static void demonstrateVision(ReActAgent agent) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Vision Capability Demo");
        System.out.println("=".repeat(80));
        System.out.println("Testing with a simple image (20x20 red square PNG)");
        System.out.println("Question: What color is this image?\n");

        try {
            // Simple 20x20 red square PNG (Base64 encoded)
            // This is a minimal valid PNG file for testing
            String redSquareBase64 =
                    "iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAIAAAAC64paAAAAFklEQVR42mP8z8DAwMj4n4FhFIw"
                            + "CMgBmBQEAAhUCYwAAAABJRU5ErkJggg==";

            // Create a message with text and base64 image
            Msg userMsg =
                    Msg.builder()
                            .role(MsgRole.USER)
                            .content(
                                    List.of(
                                            TextBlock.builder()
                                                    .text(
                                                            "What color is this image? Please"
                                                                    + " describe it.")
                                                    .build(),
                                            ImageBlock.builder()
                                                    .source(
                                                            Base64Source.builder()
                                                                    .data(redSquareBase64)
                                                                    .mediaType("image/png")
                                                                    .build())
                                                    .build()))
                            .build();

            // Send message and get response
            System.out.println("Sending request to vision model...");
            Msg response = agent.call(userMsg).block();

            // Display response
            System.out.println("\nAgent Response:");
            System.out.println("-".repeat(80));
            System.out.println(MsgUtils.getTextContent(response));
            System.out.println("-".repeat(80));
            System.out.println("\nVision capability verified successfully!");
        } catch (Exception e) {
            System.err.println("\nError analyzing image: " + e.getMessage());
            System.err.println("\nThis may indicate an issue with:");
            System.err.println("  1. API key or model access");
            System.err.println("  2. Network connectivity");
            System.err.println("  3. Model configuration");
            System.err.println(
                    "\nDon't worry - you can still test with text-only questions below.\n");
        }
    }
}
