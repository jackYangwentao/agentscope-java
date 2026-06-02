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
import java.util.List;
import java.util.Map;

/**
 * Data class that holds user input information with dual representation.
 *
 * <p>以双重表示形式保存用户输入信息的数据类。同时包含:
 * <ul>
 *   <li>内容块列表(用于构造消息)</li>
 *   <li>可选的结构化数据(用于类型化输入校验)</li>
 * </ul>
 * 这种双重性质允许在同一统一输入系统中灵活处理简单文本输入和复杂结构化表单。
 *
 * <p>Contains both content blocks (for message construction) and optional structured data
 * (for typed input validation). This dual nature allows flexible handling of simple text
 * input and complex structured forms within the same unified input system.
 */
public class UserInputData {

    private final List<ContentBlock> blocksInput;
    private final Map<String, Object> structuredInput;

    /**
     * 创建一个新的 UserInputData 实例。
     *
     * @param blocksInput 表示用户输入的内容块列表
     * @param structuredInput 用于类型化输入的可选结构化数据映射(可为 null)
     */
    public UserInputData(List<ContentBlock> blocksInput, Map<String, Object> structuredInput) {
        this.blocksInput = blocksInput;
        this.structuredInput = structuredInput;
    }

    /**
     * 获取表示用户输入的内容块列表。
     *
     * <p>内容块可包括文本、图像、音频或其他多模态内容。该表示形式适合用于构造消息对象。
     *
     * @return 内容块列表(可能为 null 或空)
     */
    public List<ContentBlock> getBlocksInput() {
        return blocksInput;
    }

    /**
     * 以键值对映射形式获取结构化输入数据。
     *
     * <p>这种可选表示形式支持类型化输入校验和复杂表单处理。
     * 例如,包含姓名、年龄和邮箱字段的表单可以表示为一个 Map。
     *
     * @return 结构化输入数据 Map,如果未提供则为 null
     */
    public Map<String, Object> getStructuredInput() {
        return structuredInput;
    }
}
