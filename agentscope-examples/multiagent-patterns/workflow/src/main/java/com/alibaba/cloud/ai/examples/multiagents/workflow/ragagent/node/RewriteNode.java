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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 在检索前对用户问题进行重写的图节点。
 *
 * <p><b>目的：</b>原始用户问题可能过于冗长或缺乏面向检索的关键词。
 * 此节点使用 LLM（通过 AgentScope 的 {@link Model}）将问题转换为简洁的、优化检索的查询，
 * 专注于实体名称、团队名称、统计数据类别等。
 *
 * <p><b>为什么需要独立节点：</b>将查询重写作为确定性的单用途节点，
 * 确保主 RAG 代理不会在改写上浪费推理周期。
 * 这是混合工作流中<b>确定性 LLM 节点</b>的经典示例。
 *
 * <p><b>流程：</b>从图状态读取 {@code "question"} → 调用轻量级 ReActAgent
 *（无工具，仅有系统提示）→ 将 {@code "rewritten_query"} 写回状态。
 */
public class RewriteNode implements NodeAction {

    private final Model model;
    private final String systemPromptTemplate;



    /**
     * @param model               用于重写的 AgentScope {@link Model}（DashScopeChatModel）
     * @param systemPromptTemplate 包含单个 {@code %s} 占位符的格式字符串，用于原始问题
     */
    public RewriteNode(Model model, String systemPromptTemplate) {
        this.model = model;
        this.systemPromptTemplate = systemPromptTemplate;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 从图状态中提取原始问题
        String question = state.value("question").map(Object::toString).orElse("");

        // 2. 使用原始问题格式化重写提示
        String prompt = systemPromptTemplate.formatted(question);

        // 3. 创建一个轻量级 ReActAgent（无工具，仅 LLM）来重写查询。
        //    使用完整的 ReAct agent 是为了复用相同的 Model 接口，
        //    但实际上只进行一次 LLM 调用——没有工具执行。
        ReActAgent rewriter =
                ReActAgent.builder()
                        .name("rewriter")
                        .sysPrompt("Respond with only the rewritten query, nothing else.")
                        .model(model)
                        .toolkit(new Toolkit())
                        .memory(new InMemoryMemory())
                        .build();

        // 4. 调用 agent 并提取重写后的文本
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();
        Msg response = rewriter.call(userMsg).block();
        String rewritten = response != null ? response.getTextContent() : null;

        // 5. 如果重写失败，回退到原始问题
        return Map.of(
                "rewritten_query", StringUtils.hasText(rewritten) ? rewritten.trim() : question);
    }
}
