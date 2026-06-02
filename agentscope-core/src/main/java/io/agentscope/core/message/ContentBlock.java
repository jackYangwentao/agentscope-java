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
package io.agentscope.core.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.agentscope.core.state.State;

/**
 * 消息中所有内容块的密封基类。
 *
 * <p>内容块表示消息中可以包含的不同类型的内容，例如文本、图片、音频、视频或推理内容。
 * 这种密封层次结构确保了类型安全，并支持穷尽模式匹配。
 *
 * <p><b>支持的内容类型：</b>
 * <ul>
 *   <li>{@link TextBlock} - 纯文本内容
 *   <li>{@link ThinkingBlock} - 智能体推理/思考内容
 *   <li>{@link ImageBlock} - 图片内容（URL 或 Base64）
 *   <li>{@link AudioBlock} - 音频内容（URL 或 Base64）
 *   <li>{@link VideoBlock} - 视频内容（URL 或 Base64）
 *   <li>{@link ToolUseBlock} - 工具执行请求
 *   <li>{@link ToolResultBlock} - 工具执行结果
 * </ul>
 *
 * <p>使用 Jackson 注解实现多态 JSON 序列化，通过 "type" 鉴别器字段进行区分。
 * sealed 修饰符将子类限制为指定的 permits 列表，实现了模式匹配的编译时穷尽性检查。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
    @JsonSubTypes.Type(value = ThinkingBlock.class, name = "thinking"),
    @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
    @JsonSubTypes.Type(value = AudioBlock.class, name = "audio"),
    @JsonSubTypes.Type(value = VideoBlock.class, name = "video"),
    @JsonSubTypes.Type(value = ToolUseBlock.class, name = "tool_use"),
    @JsonSubTypes.Type(value = ToolResultBlock.class, name = "tool_result")
})
public sealed class ContentBlock implements State
        permits TextBlock,
                ImageBlock,
                AudioBlock,
                VideoBlock,
                ThinkingBlock,
                ToolUseBlock,
                ToolResultBlock {}
