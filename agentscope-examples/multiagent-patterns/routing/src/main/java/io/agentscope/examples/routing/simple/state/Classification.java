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
 * 路由分类结果记录。
 * <p>
 * 表示路由 Agent 的一个决策：将用户查询分配给哪个子 Agent，
 * 以及分配给该 Agent 的具体子查询是什么。
 * </p>
 *
 * @param source 目标子 Agent 名称（如 "github"、"notion"、"slack"）
 * @param query  分配给该子 Agent 的子查询内容
 */
public record Classification(
        @JsonProperty("source") String source, @JsonProperty("query") String query) {

    @JsonCreator
    public Classification {}
}
