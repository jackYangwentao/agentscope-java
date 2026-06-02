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
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.tool.Toolkit;

/**
 * BasicChatExample —— 最简单的 Agent 对话示例。
 *
 * <p>展示如何使用最少的配置创建一个可对话的 AI 助手，
 * 包括 Agent、Model、Memory 和 Toolkit 的核心组件组合。
 */
public class BasicChatExample {

    public static void main(String[] args) throws Exception {
        // 打印欢迎信息
        ExampleUtils.printWelcome(
                "基础对话示例",
                "本示例演示了最简单的 Agent 设置。\n"
                        + "您将与一个由 Ollama 驱动的 AI 助手进行对话。");

        // 获取 API 密钥（从环境变量或交互输入）
        // String apiKey = ExampleUtils.getDashScopeApiKey();

        // 使用最小配置创建 Agent
        ReActAgent agent =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("你是一个有帮助的 AI 助手，请保持友好和简洁。")
                        .model(
                                OllamaChatModel.builder()
                                        .modelName("llama3.2")
                                        .baseUrl("http://localhost:11434")
                                        .build())
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        // 启动交互式对话
        ExampleUtils.startChat(agent);
    }
}
