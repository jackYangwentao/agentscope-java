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
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.higress.HigressMcpClientBuilder;
import io.agentscope.extensions.higress.HigressMcpClientWrapper;
import io.agentscope.extensions.higress.HigressToolkit;

/**
 * HigressToolExample —— 演示 Higress AI 网关与 AgentScope 的集成。
 *
 * <p>运行前需要设置 DASHSCOPE_API_KEY 环境变量。
 */
public class HigressToolExample {

    // Higress 端点地址——请替换为您自己的端点
    private static final String HIGRESS_ENDPOINT = "your higress endpoint";

    public static void main(String[] args) throws Exception {
        String apiKey = ExampleUtils.getDashScopeApiKey();

        // 1. 创建 Higress MCP 客户端
        HigressMcpClientWrapper higressClient =
                HigressMcpClientBuilder.create("higress")
                        .streamableHttpEndpoint(HIGRESS_ENDPOINT)
                        // .sseEndpoint(HIGRESS_ENDPOINT + "/sse")  // 可选：SSE 传输方式
                        // .header("Authorization", "Bearer xxx")   // 可选：添加认证头
                        // .queryParam("queryKey", "queryValue")   // 可选：添加查询参数
                        .toolSearch("your agent description", 5) // 可选：启用工具搜索
                        .buildAsync()
                        .block();

        // 2. 使用 HigressToolkit 注册
        Toolkit toolkit = new HigressToolkit();
        toolkit.registerMcpClient(higressClient).block();

        // 3. 创建带有 toolkit 的 Agent
        ReActAgent agent =
                ReActAgent.builder()
                        .name("HigressAgent")
                        .sysPrompt(
                                "You are a helpful assistant. Please answer questions concisely and"
                                        + " accurately.")
                        .model(
                                DashScopeChatModel.builder()
                                        .apiKey(apiKey)
                                        .modelName("qwen-max")
                                        .stream(true)
                                        .enableThinking(false)
                                        .formatter(new DashScopeChatFormatter())
                                        .build())
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory())
                        .build();

        // 4. Start chat
        ExampleUtils.startChat(agent);
    }
}
