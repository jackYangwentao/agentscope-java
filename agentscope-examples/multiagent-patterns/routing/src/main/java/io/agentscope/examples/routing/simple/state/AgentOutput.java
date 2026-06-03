/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.examples.routing.simple.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个专业子 Agent 的输出结果。
 * <p>
 * 记录子 Agent 的名称及其执行后返回的文本结果。
 * 在结果合成阶段，RouterService 会收集所有 AgentOutput 并合成为统一答案。
 * </p>
 *
 * @param source 子 Agent 名称（如 "github"、"notion"、"slack"）
 * @param result 子 Agent 执行后返回的文本结果
 */
public record AgentOutput(
        @JsonProperty("source") String source, @JsonProperty("result") String result) {

    @JsonCreator
    public AgentOutput {}
}
