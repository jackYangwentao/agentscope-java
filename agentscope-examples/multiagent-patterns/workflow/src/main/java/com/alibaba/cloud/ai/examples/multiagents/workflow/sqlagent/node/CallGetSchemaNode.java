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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

/**
 * 强制 LLM 调用 {@code sql_db_schema} 工具的图节点。
 *
 * <p><b>为什么存在此节点：</b>与可以确定性地列出表的 {@link ListTablesNode} 不同，
 * 选择正确的表进行模式检查需要理解用户的问题。
 * 此节点委托给 LLM（通过一个轻量级 ReActAgent，仅配备 {@code sql_db_schema} 工具）
 * 来决定哪些表是相关的。
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>从图状态中读取累积的消息和用户问题</li>
 *   <li>构建一个仅配备 {@code sql_db_schema} 工具的 ReActAgent（强制工具调用）</li>
 *   <li>LLM 的响应（包含工具调用）被转换为 Spring AI 消息格式</li>
 *   <li>同时将 {@code "messages"} 和 {@code "llm_response"} 写入图状态</li>
 * </ol>
 *
 * <p><b>状态读取：</b>{@code "messages"}, {@code "question"}
 * <br><b>状态写入：</b>{@code "messages"}, {@code "llm_response"}
 */
public class CallGetSchemaNode implements NodeAction {

    private static final String GET_SCHEMA_PROMPT =
            """
            You must call the sql_db_schema tool with a comma-separated list of table names.
            Use the available tables from the user message. Do not explain, only output the tool call.
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Model model;
    private final SqlTools sqlTools;

    public CallGetSchemaNode(Model model, SqlTools sqlTools) {
        this.model = model;
        this.sqlTools = sqlTools;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 读取累积的消息（包含 ListTablesNode 产生的表列表）
        //    以及来自图状态的原始用户问题
        List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
        String question = (String) state.value("question").orElse("");

        // 2. 从所有之前消息和问题构建上下文
        String userText = buildUserText(messages, question);

        // 3. 创建一个仅配备 sql_db_schema 工具的 ReActAgent。
        //    系统提示词强制它调用此工具而不做额外解释。
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(sqlTools);
        ReActAgent agent =
                ReActAgent.builder()
                        .name("get_schema_caller")
                        .sysPrompt(GET_SCHEMA_PROMPT)
                        .model(model)
                        .toolkit(toolkit)
                        .memory(new InMemoryMemory())
                        .build();

        // 4. 调用 agent —— 它应该产生一个 sql_db_schema 的工具调用
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(userText).build();
        Msg response = agent.call(userMsg).block();
        if (response == null) {
            return Map.of("messages", List.<Message>of());
        }

        // 5. 从响应中提取工具调用并转换为 Spring AI 格式
        List<ToolUseBlock> toolUses = response.getContentBlocks(ToolUseBlock.class);
        if (toolUses.isEmpty()) {
            // LLM 没有工具调用就响应了 —— 原样传递
            return Map.of(
                    "messages",
                    List.of(toAssistantMessage(response)),
                    "llm_response",
                    toAssistantMessage(response));
        }

        // 6. 将 AgentScope 工具调用转换为 Spring AI AssistantMessage 格式
        AssistantMessage assistantMessage = toAssistantMessage(response, toolUses);
        return Map.of("messages", List.of(assistantMessage), "llm_response", assistantMessage);
    }

    private String buildUserText(List<Message> messages, String question) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String text = m.getText();
            if (text != null && !text.isEmpty()) {
                sb.append(text).append("\n");
            }
        }
        sb.append("User question: ").append(question);
        return sb.toString();
    }

    private AssistantMessage toAssistantMessage(Msg msg) {
        return new AssistantMessage(msg.getTextContent() != null ? msg.getTextContent() : "");
    }

    private AssistantMessage toAssistantMessage(Msg msg, List<ToolUseBlock> toolUses) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (ToolUseBlock tu : toolUses) {
            String argsJson;
            try {
                argsJson =
                        JSON.writeValueAsString(tu.getInput() != null ? tu.getInput() : Map.of());
            } catch (JsonProcessingException e) {
                argsJson = "{}";
            }
            toolCalls.add(
                    new AssistantMessage.ToolCall(tu.getId(), "function", tu.getName(), argsJson));
        }
        return AssistantMessage.builder()
                .content(msg.getTextContent() != null ? msg.getTextContent() : "")
                .toolCalls(toolCalls)
                .build();
    }
}
