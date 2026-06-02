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
import io.agentscope.core.formatter.dashscope.DashScopeMultiAgentFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.pipeline.MsgHub;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.quickstart.util.MsgUtils;

/**
 * MsgHubExample —— 演示多 Agent 对话场景，使用 MsgHub 自动广播消息。
 *
 * <p>三个学生角色（Alice、Bob、Charlie）围绕一个话题展开讨论，
 * MsgHub 自动将每个学生的消息广播给其他成员。
 */
public class MsgHubExample {

    public static void main(String[] args) throws Exception {
        ExampleUtils.printWelcome(
                "MsgHub 示例 —— 多 Agent 对话",
                "本示例演示如何使用 MsgHub 进行多 Agent 对话。\n"
                    + "三位学生（Alice、Bob、Charlie）将一起讨论话题。\n"
                    + "MsgHub 自动将每个学生的消息广播给其他人。");

        // 获取 API 密钥
        String apiKey = ExampleUtils.getDashScopeApiKey();

        // 使用 MultiAgentFormatter 创建共享模型
        DashScopeChatModel model =
                DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("qwen-plus")
                        .formatter(new DashScopeMultiAgentFormatter())
                        .build();

        System.out.println("\n=== 创建三个学生 Agent ===\n");

        // 创建三个不同角色的 Agent
        ReActAgent alice =
                ReActAgent.builder()
                        .name("Alice")
                        .sysPrompt(
                                "You are Alice, an optimistic student who always sees the bright"
                                        + " side. "
                                        + "Be brief (1-2 sentences) and enthusiastic in your"
                                        + " responses.")
                        .model(model)
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        ReActAgent bob =
                ReActAgent.builder()
                        .name("Bob")
                        .sysPrompt(
                                "You are Bob, a pragmatic student who focuses on practical"
                                    + " concerns. Be brief (1-2 sentences) and realistic in your"
                                    + " responses.")
                        .model(model)
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        ReActAgent charlie =
                ReActAgent.builder()
                        .name("Charlie")
                        .sysPrompt(
                                "You are Charlie, a creative student who thinks outside the box. "
                                        + "Be brief (1-2 sentences) and imaginative in your"
                                        + " responses.")
                        .model(model)
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        System.out.println(
                "Created agents: Alice (Optimistic), Bob (Pragmatic), Charlie" + " (Creative)\n");

        // Example 1: Basic multi-agent conversation (block style)
        basicConversationExample(alice, bob, charlie);

        // Example 2: Reactive style with Mono.then() pattern
        reactiveConversationExample(alice, bob, charlie);

        System.out.println("\n=== MsgHub Example Complete ===");
        System.out.println(
                "\nKey Takeaways:"
                        + "\n1. MsgHub automates message broadcasting between agents"
                        + "\n2. Use block() for synchronous-style code"
                        + "\n3. Use then() for fully reactive code"
                        + "\n4. Each agent maintains its own memory of the conversation");
    }

    /** Example 1: Basic multi-agent conversation with automatic broadcasting. */
    private static void basicConversationExample(
            ReActAgent alice, ReActAgent bob, ReActAgent charlie) {
        System.out.println("\n=== Example 1: Basic Multi-Agent Conversation ===\n");

        // Create announcement message
        Msg announcement =
                Msg.builder()
                        .name("system")
                        .role(MsgRole.SYSTEM)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "Let's discuss: What's the best way to learn a new"
                                                        + " programming language? Each person share"
                                                        + " ONE brief idea.")
                                        .build())
                        .build();

        // Use MsgHub with try-with-resources for automatic cleanup
        try (MsgHub hub =
                MsgHub.builder()
                        .name("StudentDiscussion")
                        .participants(alice, bob, charlie)
                        .announcement(announcement)
                        .enableAutoBroadcast(true)
                        .build()) {

            // Enter the hub (sets up subscriptions and broadcasts announcement)
            hub.enter().block();

            System.out.println("Announcement: " + MsgUtils.getTextContent(announcement) + "\n");

            // Each agent responds in turn
            // Their responses are automatically broadcast to other participants
            System.out.println("[Alice's turn]");
            Msg aliceResponse = alice.call().block();
            printAgentResponse("Alice", aliceResponse);

            System.out.println("\n[Bob's turn]");
            Msg bobResponse = bob.call().block();
            printAgentResponse("Bob", bobResponse);

            System.out.println("\n[Charlie's turn]");
            Msg charlieResponse = charlie.call().block();
            printAgentResponse("Charlie", charlieResponse);

            // Verify message propagation
            System.out.println("\n--- Memory Verification ---");
            System.out.println(
                    "Alice's memory size: " + alice.getMemory().getMessages().size() + " messages");
            System.out.println(
                    "Bob's memory size: " + bob.getMemory().getMessages().size() + " messages");
            System.out.println(
                    "Charlie's memory size: "
                            + charlie.getMemory().getMessages().size()
                            + " messages");
            System.out.println(
                    "(Each agent has the announcement + all three responses in their memory)");
        }
        // Hub is automatically closed, subscribers are cleaned up
    }

    /**
     * Example 2: Reactive conversation with Mono.then() pattern. This demonstrates how to use
     * fully reactive programming style without blocking.
     */
    private static void reactiveConversationExample(
            ReActAgent alice, ReActAgent bob, ReActAgent charlie) {
        System.out.println("\n\n=== Example 2: Reactive Style with Mono.then() ===\n");

        // Clear memories from previous example
        alice.getMemory().clear();
        bob.getMemory().clear();
        charlie.getMemory().clear();

        Msg announcement =
                Msg.builder()
                        .name("system")
                        .role(MsgRole.SYSTEM)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "Quick question: What's your favorite programming"
                                                        + " paradigm?")
                                        .build())
                        .build();

        MsgHub hub =
                MsgHub.builder()
                        .name("ReactiveDiscussion")
                        .participants(alice, bob, charlie)
                        .announcement(announcement)
                        .build();

        // Fully reactive chain: enter -> alice -> bob -> charlie -> exit
        hub.enter()
                .doOnSuccess(
                        h ->
                                System.out.println(
                                        "Announcement: "
                                                + MsgUtils.getTextContent(announcement)
                                                + "\n"))
                .then(alice.call())
                .doOnSuccess(msg -> System.out.println("[Alice]: " + MsgUtils.getTextContent(msg)))
                .then(bob.call())
                .doOnSuccess(msg -> System.out.println("[Bob]: " + MsgUtils.getTextContent(msg)))
                .then(charlie.call())
                .doOnSuccess(
                        msg -> System.out.println("[Charlie]: " + MsgUtils.getTextContent(msg)))
                .then(hub.exit())
                .doOnSuccess(v -> System.out.println("\n--- Reactive chain completed ---"))
                .block(); // Only block once at the end
    }

    /** Helper method to print agent response. */
    private static void printAgentResponse(String name, Msg msg) {
        String content = MsgUtils.getTextContent(msg);
        if (content != null && !content.isEmpty()) {
            System.out.println(name + ": " + content);
        }
    }
}
