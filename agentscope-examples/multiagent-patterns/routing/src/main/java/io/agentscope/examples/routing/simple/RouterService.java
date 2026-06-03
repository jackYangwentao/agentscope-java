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
package io.agentscope.examples.routing.simple;

import com.alibaba.cloud.ai.agent.agentscope.flow.AgentScopeRoutingAgent;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.node.RoutingMergeNode;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.examples.routing.simple.state.AgentOutput;
import io.agentscope.examples.routing.simple.state.Classification;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

/**
 * 路由服务，使用 AgentScopeRoutingAgent 编排路由工作流。
 * <p>
 * 职责：
 * <ol>
 *   <li>调用路由 Agent 对用户查询进行分类，分派到合适的专业子 Agent</li>
 *   <li>并行执行子 Agent（GitHub、Notion、Slack）获取各自结果</li>
 *   <li>使用 AgentScope Model 将所有子结果合成为统一的最终答案</li>
 * </ol>
 * </p>
 */
public class RouterService {

    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    /**
     * 路由输出键集合，对应 AgentScopeRoutingAgent 中各子 Agent 的 outputKey 配置。
     * 用于从路由 Agent 的最终状态中提取各子 Agent 的执行结果。
     */
    private static final String[] OUTPUT_KEYS = {"github_key", "notion_key", "slack_key"};

    /**
     * 结果合成的 System Prompt 模板。
     * 要求模型汇总多个来源的信息，去重并组织为简洁的最终答案。
     * 包含原始用户查询，确保合成结果紧扣用户问题。
     */
    private static final String SYNTHESIZE_SYSTEM_TEMPLATE =
            """
            Synthesize these search results to answer the original question: "%s"

            - Combine information from multiple sources without redundancy
            - Highlight the most relevant and actionable information
            - Note any discrepancies between sources
            - Keep the response concise and well-organized
            """;

    /** AgentScope 模型实例，用于结果合成阶段的 LLM 调用 */
    private final Model model;

    /** AgentScope 路由 Agent，负责查询分类和并行子 Agent 分派 */
    private final AgentScopeRoutingAgent routerAgent;

    public RouterService(Model model, AgentScopeRoutingAgent routerAgent) {
        this.model = model;
        this.routerAgent = routerAgent;
    }

    /**
     * 执行完整的路由流水线：分类 → 并行子 Agent → 结果合成。
     *
     * @param query 用户原始查询
     * @return 路由结果，包含原始查询、分类列表、子 Agent 输出和最终合成答案
     * @throws GraphRunnerException 路由图执行失败时抛出
     */
    public RouterResult run(String query) throws GraphRunnerException {
        Optional<OverAllState> resultOpt = routerAgent.invoke(query);
        if (resultOpt.isEmpty()) {
            return new RouterResult(query, List.of(), List.of(), "No result from router.");
        }

        OverAllState state = resultOpt.get();
        String finalAnswer;

        List<Classification> classifications = collectClassifications(state);
        List<AgentOutput> results = collectAgentOutputs(state);
        log.debug("Routed to {} sources: {}", classifications.size(), classifications);

        Optional<Object> mergedOpt = state.value(RoutingMergeNode.DEFAULT_MERGED_OUTPUT_KEY);
        if (mergedOpt.isPresent()) {
            finalAnswer = extractText(mergedOpt.get());
        } else {
            finalAnswer = synthesize(query, results);
        }
        return new RouterResult(query, classifications, results, finalAnswer);
    }

    /**
     * 从路由 Agent 的最终状态中收集分类信息。
     * 遍历 OUTPUT_KEYS，检查每个子 Agent 是否有输出，
     * 若有则提取其对应的子查询内容构建 Classification 对象。
     */
    private List<Classification> collectClassifications(OverAllState state) {
        List<Classification> list = new ArrayList<>();
        for (String outputKey : OUTPUT_KEYS) {
            Optional<Object> outputOpt = state.value(outputKey);
            if (outputOpt.isPresent()) {
                String agentName = outputKey.replace("_key", "");
                String query = state.value(agentName + "_input").map(Object::toString).orElse("");
                list.add(new Classification(agentName, query));
            }
        }
        return list;
    }

    /**
     * 从路由 Agent 的最终状态中收集各子 Agent 的输出结果。
     * 使用 RoutingMergeNode.extractText 提取文本内容。
     */
    private List<AgentOutput> collectAgentOutputs(OverAllState state) {
        List<AgentOutput> list = new ArrayList<>();
        for (String outputKey : OUTPUT_KEYS) {
            Optional<Object> outputOpt = state.value(outputKey);
            if (outputOpt.isPresent()) {
                String agentName = outputKey.replace("_key", "");
                String result = RoutingMergeNode.extractText(outputOpt.get(), outputKey);
                list.add(new AgentOutput(agentName, result));
            }
        }
        return list;
    }

    /**
     * 从路由 Agent 的输出对象中提取纯文本内容。
     * 支持 Spring AI Message 类型和普通 Object 的 toString。
     */
    private static String extractText(Object output) {
        if (output instanceof Message message) {
            return message.getText();
        }
        return output != null ? output.toString() : "";
    }

    /**
     * 使用 AgentScope Model 将多个子 Agent 的结果合成为统一的答案。
     * <p>
     * 流程：
     * <ol>
     *   <li>将各子 Agent 结果拼接为带来源标记的文本</li>
     *   <li>使用预设的合成模板构建 System Prompt</li>
     *   <li>调用流式 API 获取模型合成结果</li>
     * </ol>
     * </p>
     *
     * @param query   原始用户查询
     * @param results 各子 Agent 的输出列表
     * @return 合成后的统一答案
     */
    public String synthesize(String query, List<AgentOutput> results) {
        if (results == null || results.isEmpty()) {
            return "No results found from any knowledge source.";
        }
        String formatted =
                results.stream()
                        .map(r -> "**From " + capitalize(r.source()) + ":**\n" + r.result())
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
        String systemPrompt = SYNTHESIZE_SYSTEM_TEMPLATE.formatted(query);
        List<Msg> messages =
                List.of(
                        Msg.builder()
                                .role(MsgRole.SYSTEM)
                                .content(TextBlock.builder().text(systemPrompt).build())
                                .build(),
                        Msg.builder()
                                .role(MsgRole.USER)
                                .content(TextBlock.builder().text(formatted).build())
                                .build());
        Flux<ChatResponse> stream = model.stream(messages, null, null);
        ChatResponse last = stream.blockLast();
        if (last == null || last.getContent() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (var block : last.getContent()) {
            if (block instanceof TextBlock tb) {
                text.append(tb.getText());
            }
        }
        return text.toString();
    }

    /**
     * 将字符串首字母大写，其余字母小写。
     * 用于格式化来源名称（如 "github" → "Github"）。
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /**
     * 路由执行的完整结果记录。
     *
     * @param query          原始用户查询
     * @param classifications 路由分类结果列表（每个子 Agent 的目标和子查询）
     * @param results         各子 Agent 的输出结果列表
     * @param finalAnswer     LLM 合成后的最终答案
     */
    public record RouterResult(
            String query,
            List<Classification> classifications,
            List<AgentOutput> results,
            String finalAnswer) {}
}
