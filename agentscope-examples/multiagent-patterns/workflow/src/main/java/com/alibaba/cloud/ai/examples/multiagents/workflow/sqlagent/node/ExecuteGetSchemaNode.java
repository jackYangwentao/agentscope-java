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
package com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.node;

import com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.tools.SqlTools;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 在 LLM 调用时执行 {@code sql_db_schema} 工具的图节点。
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>从图状态中读取最后一条消息 —— 期望是一条包含工具调用的 {@link AssistantMessage}</li>
 *   <li>过滤出 {@code sql_db_schema} 工具调用并通过 {@link SqlTools#getSchema} 执行</li>
 *   <li>将工具响应和摘要消息追加到状态中</li>
 * </ol>
 *
 * <p><b>为什么是单独的节点：</b>这将 LLM <i>请求</i> 模式的决定
 * （{@link CallGetSchemaNode}）与实际<i>执行</i>该请求分离开来。
 * 这种分离允许不涉及 LLM 的确定性工具执行，符合标准的
 * ReAct 模式（LLM 思考 → 工具执行 → LLM 观察）。
 *
 * <p><b>状态读取：</b>{@code "messages"}（最后一条消息用于工具调用）
 * <br><b>状态写入：</b>{@code "messages"}（工具响应 + 摘要）
 *
 * @see CallGetSchemaNode 产生工具调用的 LLM 节点
 * @see SqlTools#getSchema 底层工具
 */
public class ExecuteGetSchemaNode implements NodeAction {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SqlTools sqlTools;

    public ExecuteGetSchemaNode(SqlTools sqlTools) {
        this.sqlTools = sqlTools;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 从状态中获取最后一条消息 —— 应该是包含工具调用的 AssistantMessage
        List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
        Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (!(last instanceof AssistantMessage am)
                || am.getToolCalls() == null
                || am.getToolCalls().isEmpty()) {
            return Map.of(); // 没有可执行的内容
        }

        // 2. 执行消息中找到的每个 sql_db_schema 工具调用
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            if (!"sql_db_schema".equals(tc.name())) {
                continue; // 跳过非 schema 的工具调用
            }
            String tableNames = parseTableNames(tc.arguments());
            String result = sqlTools.getSchema(tableNames);
            responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
        }
        if (responses.isEmpty()) {
            return Map.of(); // 没有需要执行的 schema 调用
        }

        // 3. 为下游 generate_query agent 构建工具响应消息 + 摘要
        ToolResponseMessage toolResponse =
                ToolResponseMessage.builder().responses(responses).build();
        AssistantMessage summary =
                new AssistantMessage("Schema retrieved. Proceed to generate the SQL query.");
        return Map.of("messages", List.of(toolResponse, summary));
    }

    @SuppressWarnings("unchecked")
    private static String parseTableNames(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> map = JSON.readValue(arguments, Map.class);
            Object v = map.get("tableNames");
            return v != null ? v.toString().trim() : "";
        } catch (Exception e) {
            return arguments.trim();
        }
    }
}
