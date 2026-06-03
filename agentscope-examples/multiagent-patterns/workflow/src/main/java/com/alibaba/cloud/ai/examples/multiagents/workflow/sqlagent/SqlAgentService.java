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
package com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 调用 SQL 工作流 StateGraph 的服务类。
 *
 * <p><b>流程：</b>接收用户问题 → 调用已编译的 SQL 图 →
 * 从累积状态中提取最终的 {@link AssistantMessage}。
 *
 * <p><b>结果提取：</b>扫描 {@code "messages"} 列表中的最后一个
 * {@link AssistantMessage} —— 即 SQL 生成的最终答案。
 * 同时返回原始的 {@link OverAllState} 用于调试/检查。
 */
public class SqlAgentService {

    private final CompiledGraph graph;

    public SqlAgentService(CompiledGraph graph) {
        this.graph = graph;
    }

    /**
     * 使用给定的问题运行 SQL agent。
     */
    public SqlAgentResult run(String question) throws GraphRunnerException {
        Map<String, Object> inputs =
                Map.of("messages", List.of(new UserMessage(question)), "question", question);
        Optional<OverAllState> resultOpt = graph.invoke(inputs);

        if (resultOpt.isEmpty()) {
            return new SqlAgentResult(question, null, null);
        }

        OverAllState state = resultOpt.get();
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
        String answer =
                messages.stream()
                        .filter(m -> m instanceof AssistantMessage)
                        .map(
                                m ->
                                        m
                                                        instanceof
                                                        org.springframework.ai.chat.messages
                                                                        .AssistantMessage
                                                                am
                                                ? am.getText()
                                                : "")
                        .reduce((a, b) -> b)
                        .orElse(null);

        return new SqlAgentResult(question, answer, state);
    }

    public record SqlAgentResult(String question, String answer, OverAllState state) {}
}
