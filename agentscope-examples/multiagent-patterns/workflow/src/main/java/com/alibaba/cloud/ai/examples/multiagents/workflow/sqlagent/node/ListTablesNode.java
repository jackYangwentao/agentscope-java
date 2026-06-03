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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * 列出所有可用数据库表的图节点。
 *
 * <p><b>工作原理：</b>此节点不调用 LLM，而是创建一个<b>合成工具调用</b>
 * —— 它伪造一个 {@link AssistantMessage.ToolCall} 用于 {@code sql_db_list_tables}，
 * 直接通过 {@link SqlTools} 执行，并将工具调用 + 响应 + 摘要追加到状态中。
 *
 * <p><b>为什么使用合成工具调用：</b>这模拟了 ReAct agent 的行为，但避免了
 * LLM 往返的成本和延迟。下游的 {@code call_get_schema} 节点会收到
 * 表列表，就像由 agent 产生的一样，从而保持消息格式的一致性。
 *
 * <p><b>状态读取：</b>无（纯确定性节点）。
 * <br><b>状态写入：</b>{@code "messages"} —— 工具调用消息、工具响应和摘要。
 *
 * @see SqlTools#listTables 底层工具
 */
public class ListTablesNode implements NodeAction {

    private final SqlTools sqlTools;

    public ListTablesNode(SqlTools sqlTools) {
        this.sqlTools = sqlTools;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 生成合成工具调用 ID 并伪造 AssistantMessage，
        //    模拟 LLM 决定调用 sql_db_list_tables 的场景
        String callId = "list-tables-" + UUID.randomUUID();
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall(callId, "function", "sql_db_list_tables", "{}");
        AssistantMessage toolCallMessage =
                AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();

        // 2. 直接执行 list_tables 工具（不涉及 LLM）
        String result = sqlTools.listTables("");

        // 3. 创建工具响应，模拟来自 ToolNode 的结果
        ToolResponseMessage toolResponse =
                ToolResponseMessage.builder()
                        .responses(
                                List.of(
                                        new ToolResponseMessage.ToolResponse(
                                                callId, "sql_db_list_tables", result)))
                        .build();

        // 4. 添加摘要消息，为下游 LLM 提供上下文
        AssistantMessage responseMessage = new AssistantMessage("Available tables: " + result);

        // 5. 将三条消息追加到图状态的 messages 列表中
        List<Message> toAppend = List.of(toolCallMessage, toolResponse, responseMessage);
        return Map.of("messages", toAppend);
    }
}
