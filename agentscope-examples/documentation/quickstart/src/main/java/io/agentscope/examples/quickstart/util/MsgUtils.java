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
package io.agentscope.examples.quickstart.util;

import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.Source;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.VideoBlock;
import java.util.stream.Collectors;

/**
 * MsgUtils —— 示例中操作 Msg 消息的工具类，提供便捷的常用操作方法。
 *
 * <p>封装了从消息中提取文本、检查消息类型以及创建各种类型消息的便利方法。
 */
public class MsgUtils {

    /**
     * 从消息中提取文本内容，拼接所有文本块（TextBlock 和 ThinkingBlock）的文本。
     *
     * <p>同时包含推理内容（ThinkingBlock）和回复文本（TextBlock），中间用空行分隔。
     *
     * @param msg 要提取文本的消息
     * @return 拼接后的文本内容，如果没有文本则返回 "[No response]"
     */
    public static String getTextContent(Msg msg) {
        String thinking =
                msg.getContent().stream()
                        .filter(block -> block instanceof ThinkingBlock)
                        .map(block -> ((ThinkingBlock) block).getThinking())
                        .collect(Collectors.joining("\n"));

        String text =
                msg.getContent().stream()
                        .filter(block -> block instanceof TextBlock)
                        .map(block -> ((TextBlock) block).getText())
                        .collect(Collectors.joining("\n"));

        if (!thinking.isEmpty() && !text.isEmpty()) {
            return thinking + "\n\n" + text;
        } else if (!thinking.isEmpty()) {
            return thinking;
        } else if (!text.isEmpty()) {
            return text;
        } else {
            return "[No response]";
        }
    }

    /**
     * 检查消息是否包含文本内容（TextBlock 或 ThinkingBlock）。
     *
     * @param msg 要检查的消息
     * @return 如果消息包含文本内容则返回 true
     */
    public static boolean hasTextContent(Msg msg) {
        return msg.getContent().stream()
                .anyMatch(block -> block instanceof TextBlock || block instanceof ThinkingBlock);
    }

    /**
     * 检查消息是否包含媒体内容（图像、音频或视频块）。
     *
     * @param msg 要检查的消息
     * @return 如果消息包含媒体内容则返回 true
     */
    public static boolean hasMediaContent(Msg msg) {
        return msg.getContent().stream()
                .anyMatch(
                        block ->
                                block instanceof ImageBlock
                                        || block instanceof AudioBlock
                                        || block instanceof VideoBlock);
    }

    /**
     * 创建包含文本内容的消息（便捷方法）。
     *
     * @param name 发送者名称
     * @param role 消息角色
     * @param text 文本内容
     * @return 包含文本内容的消息
     */
    public static Msg textMsg(String name, MsgRole role, String text) {
        return Msg.builder()
                .name(name)
                .role(role)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    /**
     * 创建包含图像内容的消息（便捷方法）。
     *
     * @param name   发送者名称
     * @param role   消息角色
     * @param source 图像来源
     * @return 包含图像内容的消息
     */
    public static Msg imageMsg(String name, MsgRole role, Source source) {
        return Msg.builder()
                .name(name)
                .role(role)
                .content(ImageBlock.builder().source(source).build())
                .build();
    }

    /**
     * 创建包含音频内容的消息（便捷方法）。
     *
     * @param name   发送者名称
     * @param role   消息角色
     * @param source 音频来源
     * @return 包含音频内容的消息
     */
    public static Msg audioMsg(String name, MsgRole role, Source source) {
        return Msg.builder()
                .name(name)
                .role(role)
                .content(AudioBlock.builder().source(source).build())
                .build();
    }

    /**
     * 创建包含视频内容的消息（便捷方法）。
     *
     * @param name   发送者名称
     * @param role   消息角色
     * @param source 视频来源
     * @return 包含视频内容的消息
     */
    public static Msg videoMsg(String name, MsgRole role, Source source) {
        return Msg.builder()
                .name(name)
                .role(role)
                .content(VideoBlock.builder().source(source).build())
                .build();
    }

    /**
     * 创建包含推理内容的消息（便捷方法）。
     *
     * @param name     发送者名称
     * @param role     消息角色
     * @param thinking 推理内容
     * @return 包含推理内容的消息
     */
    public static Msg thinkingMsg(String name, MsgRole role, String thinking) {
        return Msg.builder()
                .name(name)
                .role(role)
                .content(ThinkingBlock.builder().thinking(thinking).build())
                .build();
    }

    private MsgUtils() {
        // Utility class
    }
}
