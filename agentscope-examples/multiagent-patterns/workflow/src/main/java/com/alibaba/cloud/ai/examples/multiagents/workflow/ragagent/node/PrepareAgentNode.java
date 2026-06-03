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
package com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.node;

import com.alibaba.cloud.ai.examples.multiagents.workflow.ragagent.RagAgentConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 将检索到的上下文和用户问题格式化为 agent 提示词的图节点。
 *
 * <p><b>目的：</b>此节点连接检索阶段和 agent 阶段。它从图状态读取
 * {@code "documents"}（检索到的文本块）和 {@code "question"}，
 * 将文档拼接成上下文字符串，并生成两个输出：
 * <ul>
 *   <li>{@code "messages"} — 用于图状态兼容性的 Spring AI {@code UserMessage}</li>
 *   <li>{@code "input"} — 映射到 AgentScopeAgent 指令模板中 {@code {input}} 占位符的纯文本提示</li>
 * </ul>
 *
 * <p><b>为什么需要两个输出：</b>图状态追踪 Spring AI {@link org.springframework.ai.chat.messages.Message}
 * 对象以实现可追溯性，而 AgentScopeAgent 需要通过 {@code {input}} 占位符获取简单字符串。
 */
public class PrepareAgentNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 从图状态读取原始问题和检索到的文档
        String question = state.value("question").map(Object::toString).orElse("");
        @SuppressWarnings("unchecked")
        List<String> docs = (List<String>) state.value("documents").orElse(List.of());

        // 2. 将文档拼接为单个上下文字符串，然后构建最终提示词
        String context = String.join("\n\n", docs);
        String prompt = RagAgentConfig.buildAgentPrompt(context, question);

        // 3. 返回两个条目：
        //    - "messages": Spring AI UserMessage，用于图状态可追溯性
        //    - "input":    纯文本提示词，用于 AgentScopeAgent 的 {input} 占位符
        return Map.of("messages", List.of(new UserMessage(prompt)), "input", prompt);
    }
}
