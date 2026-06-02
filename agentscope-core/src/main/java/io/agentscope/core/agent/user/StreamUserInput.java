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
package io.agentscope.core.agent.user;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 基于流的用户输入实现,从任意 InputStream 读取并写入任意 OutputStream。
 * 默认使用 System.in 和 System.out 进行控制台交互。
 *
 * <p>支持纯文本输入和通过 key=value 键值对的结构化数据输入。
 * 阻塞 I/O 操作在 bounded elastic scheduler 上执行以保持响应式兼容性。
 *
 * <p>使用示例:
 *
 * <pre>{@code
 * // 默认控制台输入/输出
 * StreamUserInput consoleInput = StreamUserInput.builder().build();
 *
 * // 自定义流(例如用于测试)
 * StreamUserInput customInput = StreamUserInput.builder()
 *     .inputStream(myInputStream)
 *     .outputStream(myOutputStream)
 *     .inputHint("Enter: ")
 *     .build();
 * }</pre>
 *
 * <p>Stream-based user input implementation that reads from any InputStream and writes to any
 * OutputStream. By default, uses System.in and System.out for console interaction.
 *
 * <p>Supports both simple text input and structured data input via key=value pairs. Blocking I/O
 * operations are executed on the bounded elastic scheduler to maintain reactive compatibility.
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * // Default console input/output
 * StreamUserInput consoleInput = StreamUserInput.builder().build();
 *
 * // Custom streams (e.g., for testing)
 * StreamUserInput customInput = StreamUserInput.builder()
 *     .inputStream(myInputStream)
 *     .outputStream(myOutputStream)
 *     .inputHint("Enter: ")
 *     .build();
 * }</pre>
 */
public class StreamUserInput implements UserInputBase {

    private final String inputHint;
    private final BufferedReader reader;
    private final PrintStream output;

    /**
     * 私有构造器 — 使用 builder() 创建实例。
     *
     * @param builder 构造器实例
     *
     * <p>Private constructor - use builder() to create instances.
     *
     * @param builder The builder instance
     */
    private StreamUserInput(Builder builder) {
        this.inputHint = builder.inputHint;
        this.reader = new BufferedReader(new InputStreamReader(builder.inputStream));
        this.output = new PrintStream(builder.outputStream);
    }

    /**
     * 创建 StreamUserInput 的 Builder。
     *
     * @return 新的 Builder 实例
     *
     * <p>Create a new builder for StreamUserInput.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从配置的输入流处理用户输入。在提示用户前打印上下文消息,
     * 读取一行文本,若提供了模型类则可选地收集结构化数据。
     * 返回包含文本内容块和可选结构化输入的 UserInputData。
     *
     * @param agentId Agent 标识符(本实现未使用)
     * @param agentName Agent 名称(本实现未使用)
     * @param contextMessages 提示前可选显示的上下文消息(如 assistant 的回复)
     * @param structuredModel 结构化输入格式的可选用类
     * @return 包含用户输入数据的 Mono
     *
     * <p>Handle user input from the configured input stream. Prints any context messages before
     * prompting the user with the configured input hint, reads a line of text, and optionally
     * collects structured data if a model class is provided. Returns a UserInputData containing
     * both the text content blocks and any structured input.
     *
     * @param agentId The agent identifier (unused in this implementation)
     * @param agentName The agent name (unused in this implementation)
     * @param contextMessages Optional messages to display before prompting (e.g., assistant
     *     response)
     * @param structuredModel Optional class for structured input format
     * @return Mono containing the user input data
     */
    @Override
    public Mono<UserInputData> handleInput(
            String agentId, String agentName, List<Msg> contextMessages, Class<?> structuredModel) {
        return Mono.fromCallable(
                        () -> {
                            try {
                                // 提示前打印上下文消息
                                // Print context messages before prompting
                                if (contextMessages != null && !contextMessages.isEmpty()) {
                                    for (Msg msg : contextMessages) {
                                        printMessage(msg);
                                    }
                                }

                                output.print(inputHint);
                                String textInput = reader.readLine();

                                if (textInput == null) {
                                    textInput = "";
                                }

                                // 创建文本块内容
                                // Create text block content
                                List<ContentBlock> blocksInput =
                                        Collections.singletonList(
                                                TextBlock.builder().text(textInput).build());

                                // 若提供了模型则处理结构化输入
                                // Handle structured input if model is provided
                                Map<String, Object> structuredInput = null;
                                if (structuredModel != null) {
                                    structuredInput = handleStructuredInput(structuredModel);
                                }

                                return new UserInputData(blocksInput, structuredInput);
                            } catch (IOException e) {
                                throw new RuntimeException("Error reading user input", e);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将消息输出到输出流。格式化为发送者名称和角色,后跟文本内容。
     *
     * @param msg 要打印的消息
     *
     * <p>Print a message to the output stream. Formats the message with the sender name and role,
     * followed by the text content.
     *
     * @param msg The message to print
     */
    private void printMessage(Msg msg) {
        StringBuilder sb = new StringBuilder();

        // 添加发送者名称和角色
        // Add sender name and role
        if (msg.getName() != null && !msg.getName().isEmpty()) {
            sb.append("[").append(msg.getName());
            if (msg.getRole() != null) {
                sb.append(" (").append(msg.getRole()).append(")");
            }
            sb.append("]: ");
        }

        // 提取并追加文本内容
        // Extract and append text content
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }

        output.println(sb.toString());
    }

    /**
     * 基于提供的模型类处理结构化输入。
     * 使用简单的 key=value 键值对解析。未来可能增强为基于反射的模型结构解析以支持字段级校验。
     *
     * @param structuredModel 定义期望结构的模型类
     * @return 包含解析后的用户输入键值对的 Map
     *
     * <p>Handle structured input based on the provided model class. Uses simple key=value pair
     * parsing. Future enhancements may include reflection-based parsing of model structure for
     * field-level validation.
     *
     * @param structuredModel The model class defining expected structure
     * @return Map containing parsed key-value pairs from user input
     */
    private Map<String, Object> handleStructuredInput(Class<?> structuredModel) {
        Map<String, Object> structuredInput = new HashMap<>();

        try {
            output.println("Structured input (press Enter to skip for optional fields):");
            output.print(
                    "\tEnter structured data as key=value pairs (or press Enter to skip): ");
            String input = reader.readLine();

            if (input != null && !input.trim().isEmpty()) {
                // 简单的 key=value 解析
                // Simple key=value parsing
                String[] pairs = input.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        structuredInput.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }
        } catch (IOException e) {
            output.println("Error reading structured input: " + e.getMessage());
        }

        return structuredInput;
    }

    /**
     * StreamUserInput 的 Builder。
     *
     * <p>提供用于配置输入/输出流和提示文本的流式 API。
     * 若未指定,默认使用 System.in/System.out。
     *
     * <p>Builder for StreamUserInput.
     *
     * <p>Provides fluent API for configuring input/output streams and prompt hint. Defaults to
     * System.in/System.out if not specified.
     */
    public static class Builder {
        private String inputHint = "User Input: ";
        private InputStream inputStream = System.in;
        private OutputStream outputStream = System.out;

        private Builder() {}

        /**
         * 设置输入提示文本。
         *
         * @param inputHint 用户输入前显示的提示文本
         * @return 当前 Builder
         *
         * <p>Set the input hint prompt.
         *
         * @param inputHint The prompt text to display before user input
         * @return This builder
         */
        public Builder inputHint(String inputHint) {
            if (inputHint != null) {
                this.inputHint = inputHint;
            }
            return this;
        }

        /**
         * 设置要读取的输入流。
         *
         * @param inputStream 输入流(默认为 System.in)
         * @return 当前 Builder
         *
         * <p>Set the input stream to read from.
         *
         * @param inputStream The input stream (defaults to System.in)
         * @return This builder
         */
        public Builder inputStream(InputStream inputStream) {
            if (inputStream != null) {
                this.inputStream = inputStream;
            }
            return this;
        }

        /**
         * 设置要写入的输出流。
         *
         * @param outputStream 输出流(默认为 System.out)
         * @return 当前 Builder
         *
         * <p>Set the output stream to write to.
         *
         * @param outputStream The output stream (defaults to System.out)
         * @return This builder
         */
        public Builder outputStream(OutputStream outputStream) {
            if (outputStream != null) {
                this.outputStream = outputStream;
            }
            return this;
        }

        /**
         * 构建 StreamUserInput 实例。
         *
         * @return 新的 StreamUserInput 实例
         *
         * <p>Build the StreamUserInput instance.
         *
         * @return A new StreamUserInput instance
         */
        public StreamUserInput build() {
            return new StreamUserInput(this);
        }
    }
}
