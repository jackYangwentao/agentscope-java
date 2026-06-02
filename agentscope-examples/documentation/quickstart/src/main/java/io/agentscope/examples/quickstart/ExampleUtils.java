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

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.examples.quickstart.util.MsgUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * ExampleUtils —— 为 quickstart 示例提供通用功能的工具类。
 *
 * <p>主要功能包括：
 * <ul>
 * <li>交互式 API 密钥配置（支持环境变量和手动输入）</li>
 * <li>Agent 对话循环实现（支持流式输出和普通调用两种模式）</li>
 * <li>用户交互的辅助方法（欢迎横幅、输入读取等）</li>
 * </ul>
 */
public class ExampleUtils {

    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));

    /**
     * 从环境变量或交互式输入获取 DashScope API 密钥。
     *
     * @return API 密钥字符串
     * @throws IOException 如果输入读取失败
     */
    public static String getDashScopeApiKey() throws IOException {
        return getApiKey(
                "DASHSCOPE_API_KEY", "DashScope", "https://dashscope.console.aliyun.com/apiKey");
    }

    /**
     * 从环境变量或交互式输入获取 OpenAI API 密钥。
     *
     * @return API 密钥字符串
     * @throws IOException 如果输入读取失败
     */
    public static String getOpenAIApiKey() throws IOException {
        return getApiKey("OPENAI_API_KEY", "OpenAI", "https://platform.openai.com/api-keys");
    }

    /**
     * 从环境变量或交互式输入获取 API 密钥的通用方法。
     *
     * <p>优先从环境变量读取，如果未设置则提示用户手动输入。
     *
     * @param envVarName  环境变量名称
     * @param serviceName 服务名称（用于显示）
     * @param helpUrl     获取 API 密钥的帮助 URL
     * @return API 密钥字符串
     * @throws IOException 如果输入读取失败
     */
    public static String getApiKey(String envVarName, String serviceName, String helpUrl)
            throws IOException {

        // 1. Try environment variable
        String apiKey = System.getenv(envVarName);

        if (apiKey != null && !apiKey.isEmpty()) {
            System.out.println("✓ Using API key from environment variable " + envVarName + "\n");
            return apiKey;
        }

        // 2. Interactive input
        System.out.println(envVarName + " environment variable not found.\n");
        System.out.println("Please enter your " + serviceName + " API Key:");
        System.out.println("(Get one at: " + helpUrl + ")");
        System.out.print("\nAPI Key: ");

        apiKey = reader.readLine().trim();

        if (apiKey.isEmpty()) {
            System.err.println("Error: API Key cannot be empty");
            System.exit(1);
        }

        System.out.println("\n✓ API Key configured");
        System.out.println("Tip: Set environment variable to skip this step:");
        System.out.println("  export " + envVarName + "=" + maskApiKey(apiKey) + "\n");

        return apiKey;
    }

    /**
     * 对 API 密钥进行脱敏处理（仅显示首 4 位和末 4 位字符）。
     *
     * @param apiKey 需要脱敏的 API 密钥
     * @return 脱敏后的 API 密钥字符串
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 启动与 Agent 的交互式对话循环。
     *
     * <p>支持流式输出（stream）和普通调用（call）两种模式，
     * 自动降级处理——流式失败时回退到普通调用。
     *
     * @param agent 要对话的 Agent 实例
     * @throws IOException 如果输入读取失败
     */
    public static void startChat(Agent agent) throws IOException {
        System.out.println("=== Chat Started ===");
        System.out.println("Type 'exit' to quit\n");

        while (true) {
            System.out.print("You> ");
            String input = reader.readLine();

            if (input == null || "exit".equalsIgnoreCase(input.trim())) {
                System.out.println("Goodbye!");
                break;
            }

            if (input.trim().isEmpty()) {
                continue;
            }

            try {
                Msg userMsg =
                        Msg.builder()
                                .role(MsgRole.USER)
                                .content(TextBlock.builder().text(input).build())
                                .build();

                System.out.print("Agent> ");

                try {
                    // Try to use stream() first for real-time output
                    AtomicBoolean hasPrintedThinkingHeader = new AtomicBoolean(false);
                    AtomicBoolean hasPrintedTextHeader = new AtomicBoolean(false);
                    AtomicBoolean hasPrintedTextSeparator = new AtomicBoolean(false);
                    AtomicReference<String> lastThinkingContent = new AtomicReference<>("");
                    AtomicReference<String> lastTextContent = new AtomicReference<>("");

                    StreamOptions streamOptions =
                            StreamOptions.builder()
                                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                                    .incremental(true)
                                    .includeReasoningResult(false)
                                    .build();

                    agent.stream(userMsg, streamOptions)
                            .doOnNext(
                                    event -> {
                                        Msg msg = event.getMessage();
                                        for (ContentBlock block : msg.getContent()) {
                                            if (block instanceof ThinkingBlock) {
                                                printStreamContent(
                                                        ((ThinkingBlock) block).getThinking(),
                                                        lastThinkingContent,
                                                        hasPrintedThinkingHeader,
                                                        "> Thinking: ",
                                                        null);
                                            } else if (block instanceof TextBlock) {
                                                printStreamContent(
                                                        ((TextBlock) block).getText(),
                                                        lastTextContent,
                                                        hasPrintedTextHeader,
                                                        "Text: ",
                                                        () -> {
                                                            if (hasPrintedThinkingHeader.get()
                                                                    && !hasPrintedTextSeparator
                                                                            .get()) {
                                                                System.out.print("\n\n");
                                                                hasPrintedTextSeparator.set(true);
                                                            }
                                                        });
                                            }
                                        }
                                    })
                            .blockLast();
                } catch (Exception e) {
                    // Fallback to call() if streaming is not supported or fails
                    if (e instanceof UnsupportedOperationException) {
                        System.err.println(
                                "\n[Info] Streaming not supported by this agent. Falling back to"
                                        + " call().");
                    } else {
                        System.err.println(
                                "\n[Warning] Exception during streaming: " + e.getMessage());
                        e.printStackTrace();
                        System.err.println("[Info] Falling back to call().");
                    }

                    Msg response = agent.call(userMsg).block();
                    if (response != null) {
                        // Extract thinking and text separately to match streaming format
                        String thinking =
                                response.getContent().stream()
                                        .filter(block -> block instanceof ThinkingBlock)
                                        .map(block -> ((ThinkingBlock) block).getThinking())
                                        .collect(Collectors.joining("\n"));

                        String text =
                                response.getContent().stream()
                                        .filter(block -> block instanceof TextBlock)
                                        .map(block -> ((TextBlock) block).getText())
                                        .collect(Collectors.joining("\n"));

                        boolean hasContent = false;
                        if (!thinking.isEmpty()) {
                            System.out.print("> Thinking: " + thinking);
                            hasContent = true;
                        }
                        if (!text.isEmpty()) {
                            if (hasContent) {
                                System.out.print("\n\n");
                            }
                            System.out.print("Text: " + text);
                            hasContent = true;
                        }
                        if (!hasContent) {
                            System.out.print("[No response]");
                        }
                    }
                }

                System.out.println("\n");

            } catch (Exception e) {
                System.err.println("\nError: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 读取用户输入的一行文本。
     *
     * @return 用户输入的字符串
     * @throws IOException 如果输入读取失败
     */
    public static String readLine() throws IOException {
        return reader.readLine();
    }

    /**
     * 打印欢迎横幅信息。
     *
     * @param title       示例标题
     * @param description 示例描述文字
     */
    public static void printWelcome(String title, String description) {
        System.out.println("=== " + title + " ===\n");
        System.out.println(description);
        System.out.println();
    }

    /**
     * 从消息中提取文本内容（委托给 MsgUtils.getTextContent）。
     *
     * @param msg 要提取文本的消息
     * @return 提取的文本内容
     */
    public static String extractTextFromMsg(Msg msg) {
        return MsgUtils.getTextContent(msg);
    }

    /**
     * 打印流式内容的辅助方法，支持增量和累积两种模式。
     *
     * <p>自动检测内容是累积模式还是增量模式，并相应地打印新增部分。
     *
     * @param content             要打印的内容
     * @param lastContentRef      上次内容的引用（用于计算增量）
     * @param hasPrintedHeaderRef 是否已打印标题的引用
     * @param header              首次打印时输出的标题
     * @param prePrintAction      打印前执行的操作（例如添加分隔符）
     */
    private static void printStreamContent(
            String content,
            AtomicReference<String> lastContentRef,
            AtomicBoolean hasPrintedHeaderRef,
            String header,
            Runnable prePrintAction) {
        String lastContent = lastContentRef.get();
        String toPrint;

        // Detect if cumulative or incremental
        if (content.startsWith(lastContent)) {
            // Cumulative: print only new part
            toPrint = content.substring(lastContent.length());
            lastContentRef.set(content);
        } else {
            // Incremental: print as-is and append
            toPrint = content;
            lastContentRef.set(lastContent + content);
        }

        if (!toPrint.isEmpty()) {
            if (prePrintAction != null) {
                prePrintAction.run();
            }

            if (!hasPrintedHeaderRef.get()) {
                System.out.print(header);
                hasPrintedHeaderRef.set(true);
            }
            System.out.print(toPrint);
            System.out.flush();
        }
    }
}
