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

import io.agentscope.core.message.Msg;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Strategy interface for handling user input from different sources.
 *
 * <p>处理来自不同来源用户输入的策略接口。支持可插拔的输入实现,如终端控制台、Web UI
 * 或程序化来源。实现类将原始用户输入转换为包含内容块和可选结构化数据的
 * {@link UserInputData},从而在不同输入通道之间保持一致。
 *
 * <p>Enables pluggable input implementations such as terminal console, web UI, or programmatic
 * sources. Implementations convert raw user input into UserInputData containing both content
 * blocks and optional structured data, maintaining consistency across different input channels.
 */
public interface UserInputBase {

    /**
     * 处理用户输入并返回输入数据。
     *
     * @param agentId Agent 标识符
     * @param agentName Agent 名称
     * @param contextMessages 提示前可选展示的消息(例如 Assistant 的回复)
     * @param structuredModel 用于结构化输入格式的可选类
     * @return 包含用户输入数据的 Mono
     */
    Mono<UserInputData> handleInput(
            String agentId, String agentName, List<Msg> contextMessages, Class<?> structuredModel);
}
