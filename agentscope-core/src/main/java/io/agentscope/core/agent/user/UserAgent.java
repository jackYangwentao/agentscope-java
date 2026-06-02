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

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * UserAgent 类,用于在 Agent 框架中处理用户交互。
 *
 * <p>充当各种用户输入源(流式、Web UI 等)与消息系统之间的桥梁。
 * 通过 UserInputBase 接口支持可插拔的输入方式,允许自定义如何收集
 * 用户输入并将其转换为框架消息。
 *
 * <p>设计理念:
 * <ul>
 *   <li>UserAgent 不管理 memory — 它只负责捕获用户输入</li>
 *   <li>通过可插拔的 UserInputBase 实现获取输入</li>
 *   <li>支持纯文本输入和带校验的结构化输入</li>
 *   <li>可参与 MsgHub 进行多 Agent 对话</li>
 * </ul>
 *
 * <p>使用示例:
 * <pre>{@code
 * // 简单的控制台输入(默认)
 * UserAgent user = UserAgent.builder()
 *     .name("User")
 *     .build();
 * Msg input = user.call().block();
 *
 * // 自定义输入方式
 * UserAgent user = UserAgent.builder()
 *     .name("User")
 *     .inputMethod(myCustomInput)
 *     .build();
 *
 * // 带 Hook
 * UserAgent user = UserAgent.builder()
 *     .name("User")
 *     .hooks(List.of(myHook))
 *     .build();
 * }</pre>
 *
 * <p>UserAgent class for handling user interaction within the agent framework.
 *
 * <p>Acts as a bridge between various user input sources (streams, web UI, etc.) and the message
 * system. Supports pluggable input methods through the UserInputBase interface, allowing
 * customization of how user input is collected and converted into framework messages.
 *
 * <p>Design Philosophy:
 * <ul>
 *   <li>UserAgent does NOT manage memory - it only captures user input</li>
 *   <li>Input is obtained via pluggable UserInputBase implementations</li>
 *   <li>Supports both simple text input and structured input with validation</li>
 *   <li>Can participate in MsgHub for multi-agent conversations</li>
 * </ul>
 *
 * <p>Usage Examples:
 * <pre>{@code
 * // Simple console input (default)
 * UserAgent user = UserAgent.builder()
 *     .name("User")
 *     .build();
 * Msg input = user.call().block();
 *
 * // With custom input method
 * UserAgent user = UserAgent.builder()
 *     .name("User")
 *     .inputMethod(myCustomInput)
 *     .build();
 *
 * // With hooks
 * UserAgent user = UserAgent.builder()
 *     .name("User")
 *     .hooks(List.of(myHook))
 *     .build();
 * }</pre>
 */
public class UserAgent extends AgentBase {

    private static UserInputBase defaultInputMethod = StreamUserInput.builder().build();
    private UserInputBase inputMethod;

    /**
     * 私有构造器 — 使用 builder() 创建实例。
     *
     * @param builder 构造器实例
     *
     * <p>Private constructor - use builder() to create instances.
     *
     * @param builder The builder instance
     */
    private UserAgent(Builder builder) {
        super(builder.name, builder.description, builder.checkRunning, builder.hooks);
        this.inputMethod = builder.inputMethod != null ? builder.inputMethod : defaultInputMethod;
    }

    /**
     * 创建 UserAgent 的 Builder。
     *
     * @return 新的 Builder 实例
     *
     * <p>Create a new builder for UserAgent.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 处理多条输入消息并生成用户输入响应。在提示用户输入前显示输入消息。
     *
     * @hidden
     * @param msgs 要显示的输入消息
     * @return 用户输入消息
     *
     * <p>Process multiple input messages and generate user input response.
     * Displays the input messages before prompting for user input.
     *
     * @hidden
     * @param msgs Input messages to display
     * @return User input message
     */
    @Override
    protected Mono<Msg> doCall(List<Msg> msgs) {
        return getUserInput(msgs, null);
    }

    /**
     * 处理多条输入消息并生成带结构化模型的用户输入响应。在提示用户输入前显示输入消息。
     *
     * @hidden
     * @param msgs 要显示的输入消息
     * @param structuredModel 定义期望输入结构的可选用类
     * @return 带 metadata 中结构化数据的用户输入消息
     *
     * <p>Process multiple input messages with structured model and generate user input response.
     * Displays the input messages before prompting for user input.
     *
     * @hidden
     * @param msgs Input messages to display
     * @param structuredModel Optional class defining the structure of expected input
     * @return User input message with structured data in metadata
     */
    @Override
    public Mono<Msg> doCall(List<Msg> msgs, Class<?> structuredModel) {
        return getUserInput(msgs, structuredModel);
    }

    /**
     * 获取用户输入,可附带上下文消息和结构化模型。
     * 这是获取用户输入的核心方法。
     *
     * @hidden
     * @param contextMessages 提示前可选显示的上下文消息
     * @param structuredModel 定义期望输入结构的可选用类
     * @return 包含用户输入消息的 Mono
     *
     * <p>Get user input with optional context messages and structured model.
     * This is the core method for obtaining user input.
     *
     * @hidden
     * @param contextMessages Optional messages to display before prompting
     * @param structuredModel Optional class defining the structure of expected input
     * @return Mono containing the user input message
     */
    public Mono<Msg> getUserInput(List<Msg> contextMessages, Class<?> structuredModel) {
        return inputMethod
                .handleInput(getAgentId(), getName(), contextMessages, structuredModel)
                .map(this::createMessageFromInput)
                .doOnNext(this::printMessage);
    }

    /**
     * 从用户输入数据创建消息。将包含内容块和可选结构化数据的 UserInputData
     * 转换为 USER 角色的框架 Msg。
     *
     * @param inputData 要转换的用户输入数据
     * @return 代表用户输入的 Msg 实例
     *
     * <p>Create a message from user input data.
     * Converts UserInputData containing content blocks and optional structured data into a
     * framework Msg with USER role.
     *
     * @param inputData The user input data to convert
     * @return A Msg instance representing the user input
     */
    private Msg createMessageFromInput(UserInputData inputData) {
        List<ContentBlock> blocksInput = inputData.getBlocksInput();
        Map<String, Object> structuredInput = inputData.getStructuredInput();

        // 将 blocks 输入转换为内容列表
        // Convert blocks input to content list
        List<ContentBlock> content;
        if (blocksInput != null && !blocksInput.isEmpty()) {
            content = blocksInput;
        } else {
            // 若无内容则创建空 text block
            // Create empty text block if no content
            content = List.of(TextBlock.builder().text("").build());
        }

        // 创建消息
        // Create the message
        Msg.Builder msgBuilder = Msg.builder().name(getName()).role(MsgRole.USER).content(content);

        // 若有结构化输入则作为 metadata 添加
        // Add structured input as metadata if present
        if (structuredInput != null && !structuredInput.isEmpty()) {
            msgBuilder.metadata(Map.of(MessageMetadataKeys.STRUCTURED_OUTPUT, structuredInput));
        }

        return msgBuilder.build();
    }

    /**
     * 将消息输出到控制台。
     *
     * <p>Print the message to console.
     */
    private void printMessage(Msg msg) {
        System.out.println(
                "[" + msg.getName() + " (" + msg.getRole() + ")]: " + extractTextFromMsg(msg));
    }

    /**
     * 提取消息中的文本内容用于展示。
     * 从 TextBlock 和 ThinkingBlock 实例中拼接文本,多个块以换行符连接。
     * 非文本块被忽略。
     *
     * @param msg 要提取文本的消息
     * @return 包含所有文本内容的字符串,若未找到则返回空字符串
     *
     * <p>Extract text content from a message for display purposes.
     * Concatenates text from TextBlock and ThinkingBlock instances, joining multiple blocks
     * with newlines. Non-text blocks are ignored.
     *
     * @param msg The message to extract text from
     * @return A string containing all text content, or empty string if none found
     */
    private String extractTextFromMsg(Msg msg) {
        return msg.getContent().stream()
                .map(
                        block -> {
                            if (block instanceof TextBlock) {
                                return ((TextBlock) block).getText();
                            } else if (block instanceof ThinkingBlock) {
                                return ((ThinkingBlock) block).getThinking();
                            }
                            return "";
                        })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 覆盖此 UserAgent 实例的输入方式。
     *
     * @hidden
     * @param inputMethod 要使用的新输入方式
     * @throws IllegalArgumentException 若 inputMethod 为 null
     *
     * <p>Override the input method for this UserAgent instance.
     *
     * @hidden
     * @param inputMethod The new input method to use
     * @throws IllegalArgumentException if inputMethod is null
     */
    protected void overrideInstanceInputMethod(UserInputBase inputMethod) {
        if (inputMethod == null) {
            throw new IllegalArgumentException("Input method cannot be null");
        }
        this.inputMethod = inputMethod;
    }

    /**
     * 覆盖所有新 UserAgent 实例的默认输入方式。
     * 这是类级别设置,影响调用后创建的实例。
     *
     * @hidden
     * @param inputMethod 新的默认输入方式
     * @throws IllegalArgumentException 若 inputMethod 为 null
     *
     * <p>Override the default input method for all new UserAgent instances.
     * This is a class-level setting that affects instances created after this call.
     *
     * @hidden
     * @param inputMethod The new default input method
     * @throws IllegalArgumentException if inputMethod is null
     */
    protected static void overrideClassInputMethod(UserInputBase inputMethod) {
        if (inputMethod == null) {
            throw new IllegalArgumentException("Input method cannot be null");
        }
        defaultInputMethod = inputMethod;
    }

    /**
     * 获取此实例的当前输入方式。
     *
     * @hidden
     * @return 当前输入方式
     *
     * <p>Get the current input method for this instance.
     *
     * @hidden
     * @return The current input method
     */
    protected UserInputBase getInputMethod() {
        return inputMethod;
    }

    /**
     * 观察消息而不产生回复。
     * UserAgent 不需要观察其他 Agent 的消息,因此为空实现。
     *
     * @hidden
     * @param msg 要观察的消息
     * @return 立即完成的 Mono
     *
     * <p>Observe messages without generating a reply.
     * UserAgent doesn't need to observe other agents' messages, so this is a no-op.
     *
     * @hidden
     * @param msg Message to observe
     * @return Mono that completes immediately
     */
    @Override
    protected Mono<Void> doObserve(Msg msg) {
        // UserAgent 不观察,直接完成
        // UserAgent doesn't observe, just complete
        return Mono.empty();
    }

    /**
     * 处理中断场景。对 UserAgent 而言,中断直接返回一条已中断消息。
     *
     * @param context 包含中断元数据的中断上下文
     * @param originalArgs 传入 call() 方法的原始参数
     * @return 包含中断消息的 Mono
     *
     * <p>Handle interrupt scenarios.
     * For UserAgent, interrupts simply return an interrupted message.
     *
     * @param context The interrupt context containing metadata about the interruption
     * @param originalArgs The original arguments passed to the call() method
     * @return Mono containing an interrupt message
     */
    @Override
    protected Mono<Msg> handleInterrupt(InterruptContext context, Msg... originalArgs) {
        Msg interruptMsg =
                Msg.builder()
                        .name(getName())
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text("Interrupted by user").build())
                        .build();

        return Mono.just(interruptMsg);
    }

    /**
     * UserAgent 的 Builder。
     *
     * <p>提供用于配置 Agent 名称、输入方式和 Hook 的流式 API。名称为必填,其他属性有合理的默认值。
     *
     * <p>Builder for UserAgent.
     *
     * <p>Provides fluent API for configuring agent name, input method, and hooks. The name is
     * required; other properties have sensible defaults.
     */
    public static class Builder {
        private String name;
        private String description;
        private boolean checkRunning = true;
        private UserInputBase inputMethod;
        private List<Hook> hooks;

        private Builder() {}

        /**
         * 设置 Agent 名称(必填)。
         *
         * @param name Agent 名称
         * @return 当前 Builder
         *
         * <p>Set the agent name (required).
         *
         * @param name The agent name
         * @return This builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置 Agent 描述。
         *
         * @param description Agent 描述
         * @return 当前 Builder
         *
         * <p>Set the agent description.
         *
         * @param description The agent description
         * @return This builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置 checkRunning 标志。
         *
         * @param checkRunning checkRunning 标志
         * @return 当前 Builder
         *
         * <p>Set the checkRunning flag.
         *
         * @param checkRunning The checkRunning flag
         * @return This builder
         */
        public Builder checkRunning(boolean checkRunning) {
            this.checkRunning = checkRunning;
            return this;
        }

        /**
         * 设置用户交互的输入方式。
         *
         * @param inputMethod 输入方式实现(默认为使用 System.in/out 的 StreamUserInput)
         * @return 当前 Builder
         *
         * <p>Set the input method for user interaction.
         *
         * @param inputMethod The input method implementation (defaults to StreamUserInput with
         *     System.in/out)
         * @return This builder
         */
        public Builder inputMethod(UserInputBase inputMethod) {
            this.inputMethod = inputMethod;
            return this;
        }

        /**
         * 设置用于监听 Agent 执行的 Hook。
         *
         * @param hooks Hook 列表
         * @return 当前 Builder
         *
         * <p>Set the hooks for monitoring agent execution.
         *
         * @param hooks List of hooks
         * @return This builder
         */
        public Builder hooks(List<Hook> hooks) {
            this.hooks = hooks;
            return this;
        }

        /**
         * 构建 UserAgent 实例。
         *
         * @return 新的 UserAgent 实例
         * @throws IllegalArgumentException 若名称为 null 或空
         *
         * <p>Build the UserAgent instance.
         *
         * @return A new UserAgent instance
         * @throws IllegalArgumentException if name is null or empty
         */
        public UserAgent build() {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Agent name is required");
            }
            return new UserAgent(this);
        }
    }
}
