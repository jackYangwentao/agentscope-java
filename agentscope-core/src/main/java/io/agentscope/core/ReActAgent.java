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
package io.agentscope.core;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.StructuredOutputCapableAgent;
import io.agentscope.core.agent.accumulator.ReasoningContext;
import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PendingToolRecoveryHook;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PostSummaryEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.hook.PreSummaryEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.hook.SummaryChunkEvent;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.interruption.InterruptSource;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.LongTermMemoryTools;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.StaticLongTermMemoryHook;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.rag.GenericRAGHook;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.KnowledgeRetrievalTools;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.session.Session;
import io.agentscope.core.shutdown.AgentShuttingDownException;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.SkillHook;
import io.agentscope.core.state.AgentMetaState;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.StatePersistence;
import io.agentscope.core.state.ToolkitState;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolResultMessageBuilder;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.util.ExceptionUtils;
import io.agentscope.core.util.MessageUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ReAct（Reasoning and Acting，推理与行动）智能体实现。
 *
 * <p>ReAct 是一种智能体设计模式，将推理（思考和规划）与行动（工具执行）在迭代循环中结合。
 * 智能体在这两个阶段之间交替进行，直到完成任务或达到最大迭代次数限制。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>响应式流式处理：</b>使用 Project Reactor 实现非阻塞执行
 *   <li><b>钩子系统：</b>可扩展的钩子用于监控和拦截智能体执行过程
 *   <li><b>HITL 支持：</b>通过 PostReasoningEvent/PostActingEvent 中的 stopAgent() 实现人机协同
 *   <li><b>结构化输出：</b>StructuredOutputCapableAgent 提供类型安全的输出生成
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 创建模型
 * DashScopeChatModel model = DashScopeChatModel.builder()
 *     .apiKey(System.getenv("DASHSCOPE_API_KEY"))
 *     .modelName("qwen-plus")
 *     .build();
 *
 * // 创建包含工具的工具包
 * Toolkit toolkit = new Toolkit();
 * toolkit.registerObject(new MyToolClass());
 *
 * // 构建智能体
 * ReActAgent agent = ReActAgent.builder()
 *     .name("助手")
 *     .sysPrompt("你是一个有用的助手。")
 *     .model(model)
 *     .toolkit(toolkit)
 *     .memory(new InMemoryMemory())
 *     .maxIters(10)
 *     .build();
 *
 * // 使用智能体
 * Msg response = agent.call(Msg.builder()
 *     .name("user")
 *     .role(MsgRole.USER)
 *     .content(TextBlock.builder().text("天气怎么样？").build())
 *     .build()).block();
 * }</pre>
 *
 * @see StructuredOutputCapableAgent
 */
public class ReActAgent extends StructuredOutputCapableAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final GracefulShutdownManager shutdownManager =
            GracefulShutdownManager.getInstance();

    // ==================== 核心依赖 ====================

    private final Memory memory;
    private final String sysPrompt;
    private final Model model;
    private final int maxIters;
    private final ExecutionConfig modelExecutionConfig;
    private final ExecutionConfig toolExecutionConfig;
    private final GenerateOptions generateOptions;
    private final PlanNotebook planNotebook;
    private final ToolExecutionContext toolExecutionContext;
    private final StatePersistence statePersistence;
    private RuntimeContext pendingRuntimeContext;

    /**
     * 每次调用的系统消息，在 PreCallEvent → PreReasoningEvent / PreSummaryEvent 之间传播。
     * 这里使用 {@link java.util.concurrent.atomic.AtomicReference} 是安全的，因为
     * {@code AgentBase.acquireExecution()} 保证每个智能体实例同时只有一个 {@code call()} 运行，
     * 所以这个引用在任何时候实际上只被单个逻辑执行所拥有。
     */
    private final java.util.concurrent.atomic.AtomicReference<Msg> currentSystemMsg =
            new java.util.concurrent.atomic.AtomicReference<>();

    // ==================== 构造函数 ====================

    private ReActAgent(Builder builder, Toolkit agentToolkit) {
        super(
                builder.name,
                builder.description,
                builder.checkRunning,
                new ArrayList<>(builder.hooks),
                agentToolkit,
                builder.structuredOutputReminder);

        this.memory = builder.memory;
        this.sysPrompt = builder.sysPrompt;
        this.model = builder.model;
        this.maxIters = builder.maxIters;
        this.modelExecutionConfig = builder.modelExecutionConfig;
        this.toolExecutionConfig = builder.toolExecutionConfig;
        this.generateOptions = builder.generateOptions;
        this.planNotebook = builder.planNotebook;
        this.toolExecutionContext = builder.toolExecutionContext;
        this.statePersistence =
                builder.statePersistence != null
                        ? builder.statePersistence
                        : StatePersistence.all();
    }

    // ==================== RuntimeContext ====================

    @Override
    protected void beforeAgentExecution(List<Msg> msgs) {
        RuntimeContext ctx = this.pendingRuntimeContext;
        this.pendingRuntimeContext = null;
        if (ctx == null) {
            ctx = RuntimeContext.empty();
        }
        // 将运行时上下文绑定到钩子
        bindRuntimeContextToHooks(ctx);
        // 重置每次调用的系统消息；将由 consumeSystemMsgAfterPreCall 初始化
        currentSystemMsg.set(null);
    }

    @Override
    protected Msg seedSystemMsg() {
        if (sysPrompt != null && !sysPrompt.trim().isEmpty()) {
            return Msg.builder()
                    .name("system")
                    .role(MsgRole.SYSTEM)
                    .content(TextBlock.builder().text(sysPrompt).build())
                    .build();
        }
        return null;
    }

    @Override
    protected void consumeSystemMsgAfterPreCall(Msg systemMsg) {
        currentSystemMsg.set(systemMsg);
    }

    @Override
    protected void afterAgentExecution() {
        unbindRuntimeContextFromHooks();
    }

    private ToolExecutionContext buildMergedToolContext() {
        RuntimeContext run = getRuntimeContext();
        if (run == null) {
            return toolExecutionContext != null
                    ? toolExecutionContext
                    : ToolExecutionContext.empty();
        }
        // 合并运行时上下文和智能体级别的工具执行上下文
        return ToolExecutionContext.merge(run.asToolExecutionContext(), toolExecutionContext);
    }

    /**
     * 使用每次调用的 {@link RuntimeContext} 调用智能体（用于钩子和工具的元数据，不持久化）。
     */
    public Mono<Msg> call(List<Msg> msgs, RuntimeContext context) {
        this.pendingRuntimeContext = context;
        return call(msgs);
    }

    public Mono<Msg> call(List<Msg> msgs, Class<?> structuredOutputClass, RuntimeContext context) {
        this.pendingRuntimeContext = context;
        return call(msgs, structuredOutputClass);
    }

    public Mono<Msg> call(List<Msg> msgs, JsonNode outputSchema, RuntimeContext context) {
        this.pendingRuntimeContext = context;
        return call(msgs, outputSchema);
    }

    public Flux<Event> stream(List<Msg> msgs, StreamOptions options, RuntimeContext context) {
        this.pendingRuntimeContext = context;
        return stream(msgs, options);
    }

    public Flux<Event> stream(
            List<Msg> msgs,
            StreamOptions options,
            Class<?> structuredModel,
            RuntimeContext context) {
        this.pendingRuntimeContext = context;
        return stream(msgs, options, structuredModel);
    }

    public Flux<Event> stream(
            List<Msg> msgs, StreamOptions options, JsonNode schema, RuntimeContext context) {
        this.pendingRuntimeContext = context;
        return stream(msgs, options, schema);
    }

    // ==================== 新的 StateModule API ====================

    /**
     * 使用新 API 将智能体状态保存到会话中。
     *
     * <p>此方法根据 StatePersistence 配置保存所有托管组件的状态：
     *
     * <ul>
     *   <li>智能体元数据（始终保存）
     *   <li>记忆消息（如果 memoryManaged 为 true）
     *   <li>Toolkit activeGroups（如果 toolkitManaged 为 true）
     *   <li>PlanNotebook 状态（如果 planNotebookManaged 为 true）
     * </ul>
     *
     * @param session 要保存状态的会话
     * @param sessionKey 会话标识符
     */
    @Override
    public void saveTo(Session session, SessionKey sessionKey) {
        // Save agent metadata
        session.save(
                sessionKey,
                "agent_meta",
                new AgentMetaState(getAgentId(), getName(), getDescription(), sysPrompt));

        // Save memory if managed
        if (statePersistence.memoryManaged()) {
            memory.saveTo(session, sessionKey);
        }

        // Save toolkit activeGroups if managed
        if (statePersistence.toolkitManaged() && toolkit != null) {
            session.save(
                    sessionKey,
                    "toolkit_activeGroups",
                    new ToolkitState(toolkit.getActiveGroups()));
        }

        // Save PlanNotebook if managed
        if (statePersistence.planNotebookManaged() && planNotebook != null) {
            planNotebook.saveTo(session, sessionKey);
        }
    }

    /**
     * 从会话中使用新 API 加载智能体状态。
     *
     * <p>此方法根据 StatePersistence 配置加载所有托管组件的状态。
     *
     * @param session 要加载状态的会话
     * @param sessionKey 会话标识符
     */
    @Override
    public boolean loadIfExists(Session session, SessionKey sessionKey) {
        shutdownManager.bindSession(this, session, sessionKey);
        return super.loadIfExists(session, sessionKey);
    }

    @Override
    public void loadFrom(Session session, SessionKey sessionKey) {
        shutdownManager.bindSession(this, session, sessionKey);
        // Load memory if managed
        if (statePersistence.memoryManaged()) {
            memory.loadFrom(session, sessionKey);
        }

        // Load toolkit activeGroups if managed
        if (statePersistence.toolkitManaged() && toolkit != null) {
            session.get(sessionKey, "toolkit_activeGroups", ToolkitState.class)
                    .ifPresent(state -> toolkit.setActiveGroups(state.activeGroups()));
        }

        // Load PlanNotebook if managed
        if (statePersistence.planNotebookManaged() && planNotebook != null) {
            planNotebook.loadFrom(session, sessionKey);
        }
    }

    // ==================== 受保护的 API ====================

    @Override
    protected Mono<Msg> doCall(List<Msg> msgs) {
        // 获取待处理的工具调用 ID
        Set<String> pendingIds = getPendingToolUseIds();

        // 没有待处理工具 -> 正常处理
        if (pendingIds.isEmpty()) {
            addToMemory(msgs);
            return executeIteration(0);
        }

        // 有待处理工具但没有输入 -> 恢复执行（直接执行待处理工具）
        if (msgs == null || msgs.isEmpty()) {
            return acting(0);
        }

        // 有待处理工具 + 有输入 -> 检查用户是否提供了工具结果
        List<ToolResultBlock> providedResults =
                msgs.stream()
                        .flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream())
                        .toList();

        if (!providedResults.isEmpty()) {
            // 用户提供了工具结果 -> 验证并添加
            validateAndAddToolResults(msgs, pendingIds);
            return hasPendingToolUse() ? acting(0) : executeIteration(0);
        }

        // 如果启用了 PendingToolRecoveryHook，待处理状态应该在 PreCallEvent 期间被修补。
        // 如果仍然到达这里，说明钩子被禁用且用户未提供工具结果 —— 这是不可恢复的状态。
        throw new IllegalStateException(
                "存在待处理工具调用但没有结果。"
                        + "请启用 PendingToolRecoveryHook 或提供工具结果。"
                        + "待处理 ID: "
                        + pendingIds);
    }

    /**
     * 构建表示工具执行错误的 {@link ToolResultBlock}。
     *
     * @param toolId 失败的工具调用的 ID
     * @param errorMessage 人类可读的错误描述
     * @return 包含格式化错误消息的 {@link ToolResultBlock}
     */
    private static ToolResultBlock buildErrorToolResult(String toolId, String errorMessage) {
        return ToolResultBlock.builder()
                .id(toolId)
                .output(List.of(TextBlock.builder().text("[ERROR] " + errorMessage).build()))
                .build();
    }

    /**
     * 在记忆中查找最后一条助手消息。
     *
     * @return 最后一条助手消息，如果未找到则返回 null
     */
    private Msg findLastAssistantMsg() {
        List<Msg> memoryMsgs = memory.getMessages();
        for (int i = memoryMsgs.size() - 1; i >= 0; i--) {
            Msg msg = memoryMsgs.get(i);
            if (msg.getRole() == MsgRole.ASSISTANT) {
                return msg;
            }
        }
        return null;
    }

    /**
     * 检查是否存在没有对应结果的待处理工具调用。
     *
     * @return 如果存在待处理工具调用则返回 true
     */
    private boolean hasPendingToolUse() {
        return !getPendingToolUseIds().isEmpty();
    }

    /**
     * 从最后一条助手消息中获取待处理工具调用 ID 集合。
     *
     * @return 在记忆中没有对应结果的待处理工具调用 ID 集合
     */
    private Set<String> getPendingToolUseIds() {
        Msg lastAssistant = findLastAssistantMsg();
        if (lastAssistant == null || !lastAssistant.hasContentBlocks(ToolUseBlock.class)) {
            return Set.of();
        }

        Set<String> existingResultIds =
                memory.getMessages().stream()
                        .flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream())
                        .map(ToolResultBlock::getId)
                        .collect(Collectors.toSet());

        return lastAssistant.getContentBlocks(ToolUseBlock.class).stream()
                .map(ToolUseBlock::getId)
                .filter(id -> !existingResultIds.contains(id))
                .collect(Collectors.toSet());
    }

    /**
     * 当存在待处理工具调用时验证输入消息，然后添加到记忆中。
     *
     * <p>验证规则：
     * <ul>
     *   <li>空输入：无操作（将继续执行 acting）</li>
     *   <li>没有工具结果：抛出错误</li>
     *   <li>有工具结果：验证 ID 匹配待处理 ID，无重复</li>
     *   <li>部分结果 + 文本内容：抛出错误（只有在所有工具完成时才允许文本）</li>
     * </ul>
     *
     * @param msgs 要验证的输入消息
     * @param pendingIds 待处理工具调用 ID 集合
     * @throws IllegalStateException 如果验证失败
     */
    private void validateAndAddToolResults(List<Msg> msgs, Set<String> pendingIds) {
        if (msgs == null || msgs.isEmpty()) {
            return;
        }

        List<ToolResultBlock> results =
                msgs.stream()
                        .flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream())
                        .toList();

        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot add messages without tool results when pending tool calls exist. "
                            + "Pending IDs: "
                            + pendingIds);
        }

        // Check for duplicate IDs
        Set<String> providedIds = new HashSet<>();
        for (ToolResultBlock r : results) {
            if (!providedIds.add(r.getId())) {
                throw new IllegalStateException("Duplicate tool result ID: " + r.getId());
            }
        }

        // Check all provided IDs match pending IDs
        Set<String> invalidIds =
                providedIds.stream()
                        .filter(id -> !pendingIds.contains(id))
                        .collect(Collectors.toSet());
        if (!invalidIds.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid tool result IDs: " + invalidIds + ". Expected: " + pendingIds);
        }

        // Check for non-ToolResultBlock content
        boolean hasTextContent =
                msgs.stream()
                        .flatMap(m -> m.getContent().stream())
                        .anyMatch(block -> !(block instanceof ToolResultBlock));

        // If only partial results provided, text content is not allowed
        boolean isPartialResults = !providedIds.containsAll(pendingIds);
        if (isPartialResults && hasTextContent) {
            throw new IllegalStateException(
                    "Cannot include text content when providing partial tool results. "
                            + "Provided: "
                            + providedIds
                            + ", Pending: "
                            + pendingIds);
        }

        msgs.forEach(memory::addMessage);
    }

    /**
     * 将消息添加到记忆中（如果不为 null）。
     *
     * @param msgs 要添加的消息
     */
    private void addToMemory(List<Msg> msgs) {
        if (msgs != null) {
            msgs.forEach(memory::addMessage);
        }
    }

    // ==================== 核心 ReAct 循环 ====================

    private Mono<Msg> executeIteration(int iter) {
        return reasoning(iter, false);
    }

    /**
     * 执行推理阶段。
     *
     * <p>此方法从模型流式传输响应，累积块，通知钩子，
     * 并决定是否继续执行行动阶段或提前返回（HITL 停止、gotoReasoning 或已完成）。
     *
     * @param iter 当前迭代次数
     * @param ignoreMaxIters 如果为 true，跳过 maxIters 检查（用于 gotoReasoning）
     * @return 包含最终结果消息的 Mono
     */
    private Mono<Msg> reasoning(int iter, boolean ignoreMaxIters) {
        // Check maxIters unless ignoreMaxIters is set
        if (!ignoreMaxIters && iter >= maxIters) {
            return summarizing();
        }

        ReasoningContext context = new ReasoningContext(getName());

        return checkInterruptedAsync()
                .then(notifyPreReasoningEvent(memory.getMessages()))
                .flatMapMany(
                        event -> {
                            GenerateOptions options =
                                    event.getEffectiveGenerateOptions() != null
                                            ? event.getEffectiveGenerateOptions()
                                            : buildGenerateOptions();
                            List<Msg> modelInput =
                                    prependSystemMsg(
                                            event.getInputMessages(), event.getSystemMessage());
                            return model.stream(modelInput, toolkit.getToolSchemas(), options)
                                    .concatMap(chunk -> checkInterruptedAsync().thenReturn(chunk));
                        })
                .doOnNext(
                        chunk -> {
                            List<Msg> chunkMsgs = context.processChunk(chunk);
                            // Notify streaming hooks for each chunk message
                            for (Msg msg : chunkMsgs) {
                                notifyReasoningChunk(msg, context).subscribe();
                            }
                        })
                .then(Mono.defer(() -> Mono.justOrEmpty(context.buildFinalMessage())))
                .onErrorResume(
                        InterruptedException.class,
                        error -> {
                            Msg msg = context.buildFinalMessage();
                            if (msg != null) {
                                boolean discard =
                                        getInterruptSource() == InterruptSource.SYSTEM
                                                && shutdownManager
                                                                .getConfig()
                                                                .partialReasoningPolicy()
                                                        == PartialReasoningPolicy.DISCARD;
                                // Manually interruption will save the msg, while system
                                // interruption will discard on specific config
                                if (!discard) {
                                    memory.addMessage(msg);
                                }
                            }
                            return Mono.error(error);
                        })
                .flatMap(this::notifyPostReasoning)
                .flatMap(
                        event -> {
                            Msg msg = event.getReasoningMessage();
                            if (msg != null) {
                                memory.addMessage(msg);
                            }

                            // HITL 停止
                            if (event.isStopRequested()) {
                                return Mono.just(
                                        msg.withGenerateReason(
                                                GenerateReason.REASONING_STOP_REQUESTED));
                            }

                            // 请求 gotoReasoning（例如由 StructuredOutputHook 触发）
                            if (event.isGotoReasoningRequested()) {
                                // 验证已在 PostReasoningEvent.gotoReasoning() 中完成
                                List<Msg> gotoMsgs = event.getGotoReasoningMsgs();
                                if (gotoMsgs != null) {
                                    gotoMsgs.forEach(memory::addMessage);
                                }
                                // 继续下一次迭代，忽略此次入口的 maxIters
                                return reasoning(iter + 1, true);
                            }

                            // 检查完成条件
                            if (isFinished(msg)) {
                                return Mono.just(msg);
                            }

                            // 继续执行行动阶段
                            return checkInterruptedAsync().then(acting(iter));
                        })
                .switchIfEmpty(
                        Mono.defer(
                                () -> {
                                    // No message was produced
                                    return Mono.justOrEmpty((Msg) null);
                                }));
    }

    /**
     * 执行行动阶段。
     *
     * <p>此方法仅执行待处理工具（记忆中没有结果的工具），
     * 为成功的工具结果通知钩子，并决定是否继续迭代或返回
     * （HITL 停止、挂起工具或结构化输出）。
     *
     * <p>对于抛出 {@link io.agentscope.core.tool.ToolSuspendException} 的工具：
     * <ul>
     *   <li>Toolkit 捕获异常并将其转换为待处理的 ToolResultBlock</li>
     *   <li>成功结果存储在记忆中，待处理结果不存储</li>
     *   <li>返回包含挂起的 ToolUseBlocks 的 Msg，生成原因为 {@link GenerateReason#TOOL_SUSPENDED}</li>
     * </ul>
     *
     * @param iter 当前迭代次数
     * @return 包含最终结果消息的 Mono
     */
    private Mono<Msg> acting(int iter) {
        // 仅提取待处理工具调用（记忆中没有结果的工具）
        List<ToolUseBlock> pendingToolCalls = extractPendingToolCalls();

        if (pendingToolCalls.isEmpty()) {
            // 没有待处理工具已执行，继续下一次迭代
            return executeIteration(iter + 1);
        }

        // 将工具块转发到 ActingChunkEvent 钩子，不覆盖用户回调。
        toolkit.setInternalChunkCallback(
                (toolUse, chunk) -> notifyActingChunk(toolUse, chunk).subscribe());

        // 仅执行待处理工具（记忆中没有结果的工具）
        return notifyPreActingHooks(pendingToolCalls)
                .flatMap(this::executeToolCalls)
                .flatMap(
                        results -> {
                            // 分离成功和待处理结果
                            List<Map.Entry<ToolUseBlock, ToolResultBlock>> successPairs =
                                    results.stream()
                                            .filter(e -> !e.getValue().isSuspended())
                                            .toList();
                            List<Map.Entry<ToolUseBlock, ToolResultBlock>> pendingPairs =
                                    results.stream()
                                            .filter(e -> e.getValue().isSuspended())
                                            .toList();

                            // 如果没有成功结果要处理
                            if (successPairs.isEmpty()) {
                                if (!pendingPairs.isEmpty()) {
                                    return Mono.just(buildSuspendedMsg(pendingPairs));
                                }
                                return executeIteration(iter + 1);
                            }

                            // 通过钩子处理成功结果并添加到记忆中
                            return Flux.fromIterable(successPairs)
                                    .concatMap(this::notifyPostActingHook)
                                    .last()
                                    .flatMap(
                                            event -> {
                                                // HITL 停止（也由 StructuredOutputHook 在完成时触发）
                                                if (event.isStopRequested()) {
                                                    return Mono.just(
                                                            event.getToolResultMsg()
                                                                    .withGenerateReason(
                                                                            GenerateReason
                                                                                    .ACTING_STOP_REQUESTED));
                                                }

                                                // 如果存在待处理结果，构建挂起消息
                                                if (!pendingPairs.isEmpty()) {
                                                    return Mono.just(
                                                            buildSuspendedMsg(pendingPairs));
                                                }

                                                // 继续下一次迭代
                                                return executeIteration(iter + 1);
                                            });
                        });
    }

    /**
     * 构建包含挂起工具调用的消息以供用户执行。
     *
     * <p>该消息同时包含挂起工具的 ToolUseBlocks 和对应的待处理 ToolResultBlocks。
     *
     * @param pendingPairs (ToolUseBlock, 待处理 ToolResultBlock) 对列表
     * @return 生成原因为 GenerateReason.TOOL_SUSPENDED 的消息
     */
    private Msg buildSuspendedMsg(List<Map.Entry<ToolUseBlock, ToolResultBlock>> pendingPairs) {
        List<ContentBlock> content = new ArrayList<>();
        for (Map.Entry<ToolUseBlock, ToolResultBlock> pair : pendingPairs) {
            content.add(pair.getKey());
            content.add(pair.getValue());
        }
        return Msg.builder()
                .name(getName())
                .role(MsgRole.ASSISTANT)
                .content(content)
                .generateReason(GenerateReason.TOOL_SUSPENDED)
                .build();
    }

    /**
     * 执行工具调用并返回配对结果。
     *
     * <p>如果工具执行失败（超时、错误等），此方法会为所有待处理工具调用生成错误工具结果，
     * 而不是传播错误。这确保智能体可以继续处理，模型会收到适当的错误反馈。
     *
     * @param toolCalls 工具调用列表（可能已被 PreActingEvent 钩子修改）
     * @return 包含 (ToolUseBlock, ToolResultBlock) 对列表的 Mono
     */
    private Mono<List<Map.Entry<ToolUseBlock, ToolResultBlock>>> executeToolCalls(
            List<ToolUseBlock> toolCalls) {
        return toolkit.callTools(toolCalls, toolExecutionConfig, this, buildMergedToolContext())
                .map(
                        results ->
                                IntStream.range(0, toolCalls.size())
                                        .mapToObj(i -> Map.entry(toolCalls.get(i), results.get(i)))
                                        .toList())
                .onErrorResume(
                        Exception.class,
                        error -> {
                            // 保留中断信号以用于智能体停止策略
                            if (error instanceof InterruptedException) {
                                return Mono.error(error);
                            }
                            // 为所有待处理工具调用生成错误工具结果。
                            // 仅捕获 Exception 子类；关键 JVM 错误（如 OutOfMemoryError）允许传播。
                            String errorMsg = ExceptionUtils.getErrorMessage(error);
                            log.error(
                                    "工具执行失败，为 {} 个工具调用生成错误结果",
                                    toolCalls.size(),
                                    error);
                            List<Map.Entry<ToolUseBlock, ToolResultBlock>> errorResults =
                                    toolCalls.stream()
                                            .map(
                                                    toolCall -> {
                                                        ToolResultBlock errorResult =
                                                                buildErrorToolResult(
                                                                        toolCall.getId(),
                                                                        "Tool execution failed: "
                                                                                + errorMsg);
                                                        return Map.entry(toolCall, errorResult);
                                                    })
                                            .toList();
                            return Mono.just(errorResults);
                        });
    }

    /**
     * 为单个工具结果通知 PostActingEvent 钩子，构建消息并添加到记忆中。
     */
    private Mono<PostActingEvent> notifyPostActingHook(
            Map.Entry<ToolUseBlock, ToolResultBlock> entry) {
        ToolUseBlock toolUse = entry.getKey();
        ToolResultBlock result = entry.getValue();

        // 首先构建工具结果消息，以便钩子可以访问它
        Msg toolMsg = ToolResultMessageBuilder.buildToolResultMsg(result, toolUse, getName());

        // 创建已设置 toolResultMsg 的事件
        PostActingEvent event = new PostActingEvent(this, toolkit, toolUse, result);
        event.setToolResultMsg(toolMsg);

        // 通知钩子并添加到记忆中
        return notifyHooks(event).doOnNext(e -> memory.addMessage(e.getToolResultMsg()));
    }

    /**
     * 在达到最大迭代次数时生成摘要。
     */
    protected Mono<Msg> summarizing() {
        log.debug("已达到最大迭代次数。正在生成摘要...");

        // 处理在达到最大迭代次数之前未完成的待处理工具调用
        if (hasPendingToolUse()) {
            List<ToolUseBlock> pendingTools = extractPendingToolCalls();
            log.warn(
                    "达到最大迭代次数，仍有 {} 个待处理工具调用。正在添加错误结果。",
                    pendingTools.size());

            for (ToolUseBlock toolUse : pendingTools) {
                ToolResultBlock errorResult =
                        buildErrorToolResult(
                                toolUse.getId(),
                                "工具执行已取消，因为已达到最大迭代次数限制 ("
                                        + maxIters
                                        + ")");

                Msg errorResultMsg =
                        ToolResultMessageBuilder.buildToolResultMsg(
                                errorResult, toolUse, getName());
                memory.addMessage(errorResultMsg);
            }
        }

        List<Msg> messageList = prepareSummaryMessages();
        GenerateOptions generateOptions = buildGenerateOptions();

        return notifyPreSummaryHook(messageList, generateOptions)
                .flatMap(
                        preSummaryEvent -> {
                            List<Msg> effectiveMessages =
                                    prependSystemMsg(
                                            preSummaryEvent.getInputMessages(),
                                            preSummaryEvent.getSystemMessage());
                            GenerateOptions effectiveOptions =
                                    preSummaryEvent.getEffectiveGenerateOptions();

                            return streamAndAccumulateSummary(effectiveMessages, effectiveOptions)
                                    .flatMap(
                                            msg ->
                                                    notifyPostSummaryHook(msg, effectiveOptions)
                                                            .map(
                                                                    postEvent -> {
                                                                        Msg finalMsg =
                                                                                postEvent
                                                                                        .getSummaryMessage()
                                                                                        .withGenerateReason(
                                                                                                GenerateReason
                                                                                                        .MAX_ITERATIONS);
                                                                        memory.addMessage(finalMsg);
                                                                        return finalMsg;
                                                                    }));
                        })
                .onErrorResume(this::handleSummaryError);
    }

    private Mono<Msg> streamAndAccumulateSummary(
            List<Msg> messages, GenerateOptions generateOptions) {
        return model.stream(messages, null, generateOptions)
                .concatMap(chunk -> checkInterruptedAsync().thenReturn(chunk))
                .reduce(
                        new ReasoningContext(getName()),
                        (ctx, chunk) -> {
                            List<Msg> streamedMessages = ctx.processChunk(chunk);
                            for (Msg streamedMessage : streamedMessages) {
                                notifySummaryChunk(streamedMessage, ctx, generateOptions)
                                        .subscribe();
                            }
                            return ctx;
                        })
                .map(ReasoningContext::buildFinalMessage);
    }

    private List<Msg> prepareSummaryMessages() {
        List<Msg> messageList = new ArrayList<>(memory.getMessages());
        messageList.add(
                Msg.builder()
                        .name("user")
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "You have failed to generate response within the"
                                                    + " maximum iterations. Now respond directly by"
                                                    + " summarizing the current situation.")
                                        .build())
                        .build());
        return messageList;
    }

    private Mono<Msg> handleSummaryError(Throwable error) {
        if (error instanceof InterruptedException) {
            return Mono.error(error);
        }
        log.error("生成摘要时出错", error);
        Msg errorMsg =
                Msg.builder()
                        .name(getName())
                        .role(MsgRole.ASSISTANT)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                String.format(
                                                        "已达到最大迭代次数 (%d)。"
                                                                + "生成摘要时出错: %s",
                                                        maxIters, error.getMessage()))
                                        .build())
                        .build();
        memory.addMessage(errorMsg);
        return Mono.just(errorMsg);
    }

    // ==================== 辅助方法 ====================

    /**
     * 如果系统消息非空，则将其前置到 {@code msgs} 中。
     *
     * <p>在每次 {@code model.stream()} 调用之前立即调用，以构建最终的 LLM 输入，
     * 而不污染内存中的消息列表。
     */
    private static List<Msg> prependSystemMsg(List<Msg> msgs, Msg systemMsg) {
        if (systemMsg == null) {
            return msgs != null ? msgs : List.of();
        }
        List<Msg> result = new ArrayList<>();
        result.add(systemMsg);
        if (msgs != null) {
            result.addAll(msgs);
        }
        return result;
    }

    /**
     * 检查 ReAct 循环是否应该终止。
     *
     * <p>注意：结构化输出重试现在由 StructuredOutputHook 通过 gotoReasoning() 处理。
     *
     * @param msg 推理消息
     * @return 如果应该完成则返回 true，如果应该继续执行行动阶段则返回 false
     */
    private boolean isFinished(Msg msg) {
        if (msg == null) {
            return true;
        }

        List<ToolUseBlock> toolCalls = msg.getContentBlocks(ToolUseBlock.class);

        // 没有工具调用 - 已完成
        // 如果存在工具调用（即使是不存在的），继续执行行动阶段，
        // ToolExecutor 将为模型返回“未找到工具”错误
        return toolCalls.isEmpty();
    }

    /**
     * 从最近的助手消息中提取工具调用。
     */
    private List<ToolUseBlock> extractRecentToolCalls() {
        return MessageUtils.extractRecentToolCalls(memory.getMessages(), getName());
    }

    /**
     * 仅从最近的助手消息中提取待处理工具调用（记忆中没有结果的工具）。
     *
     * <p>此方法过滤掉记忆中已有对应结果的工具调用，
     * 防止在从 HITL 或部分工具结果场景恢复时重复执行。
     *
     * @return 尚未有结果的待处理工具使用块列表，如果所有工具都已执行则返回空列表
     */
    private List<ToolUseBlock> extractPendingToolCalls() {
        List<ToolUseBlock> allToolCalls = extractRecentToolCalls();
        if (allToolCalls.isEmpty()) {
            return List.of();
        }

        Set<String> pendingIds = getPendingToolUseIds();
        return allToolCalls.stream()
                .filter(toolUse -> pendingIds.contains(toolUse.getId()))
                .toList();
    }

    @Override
    protected GenerateOptions buildGenerateOptions() {
        // 如果可用，从用户配置的 generateOptions 开始
        GenerateOptions baseOptions = generateOptions;

        // 如果设置了 modelExecutionConfig，将其合并到选项中
        if (modelExecutionConfig != null) {
            GenerateOptions execConfigOptions =
                    GenerateOptions.builder().executionConfig(modelExecutionConfig).build();
            baseOptions = GenerateOptions.mergeOptions(execConfigOptions, baseOptions);
        }

        return baseOptions != null ? baseOptions : GenerateOptions.builder().build();
    }

    // ==================== 钩子通知方法 ====================

    /**
     * 通用钩子通知方法。
     */
    private <T extends HookEvent> Mono<T> notifyHooks(T event) {
        Mono<T> result = Mono.just(event);
        for (Hook hook : getSortedHooks()) {
            result = result.flatMap(hook::onEvent);
        }
        return result;
    }

    private Mono<PreReasoningEvent> notifyPreReasoningEvent(List<Msg> msgs) {
        PreReasoningEvent event = new PreReasoningEvent(this, model.getModelName(), null, msgs);
        event.setSystemMessage(currentSystemMsg.get());
        return notifyHooks(event);
    }

    private Mono<PostReasoningEvent> notifyPostReasoning(Msg msg) {
        return notifyHooks(new PostReasoningEvent(this, model.getModelName(), null, msg));
    }

    private Mono<List<ToolUseBlock>> notifyPreActingHooks(List<ToolUseBlock> toolCalls) {
        return Flux.fromIterable(toolCalls)
                .concatMap(tool -> notifyHooks(new PreActingEvent(this, toolkit, tool)))
                .map(PreActingEvent::getToolUse)
                .collectList();
    }

    private Mono<Void> notifyActingChunk(ToolUseBlock toolUse, ToolResultBlock chunk) {
        ActingChunkEvent event =
                new ActingChunkEvent(
                        this,
                        toolkit,
                        toolUse,
                        chunk.withIdAndName(toolUse.getId(), toolUse.getName()));
        return Flux.fromIterable(getSortedHooks()).flatMap(hook -> hook.onEvent(event)).then();
    }

    private Mono<Void> notifyReasoningChunk(Msg chunkMsg, ReasoningContext context) {
        ContentBlock content = chunkMsg.getFirstContentBlock();

        ContentBlock accumulatedContent = null;
        if (content instanceof TextBlock) {
            accumulatedContent = TextBlock.builder().text(context.getAccumulatedText()).build();
        } else if (content instanceof ThinkingBlock) {
            accumulatedContent =
                    ThinkingBlock.builder().thinking(context.getAccumulatedThinking()).build();
        } else if (content instanceof ToolUseBlock tub) {
            // Support streaming ToolUseBlock events
            ToolUseBlock accumulated = context.getAccumulatedToolCall(tub.getId());
            if (accumulated != null) {
                accumulatedContent = accumulated;
            } else {
                // If no accumulated data, use the current chunk directly
                accumulatedContent = tub;
            }
        }

        if (accumulatedContent != null) {
            Msg accumulated =
                    Msg.builder()
                            .id(chunkMsg.getId())
                            .name(chunkMsg.getName())
                            .role(chunkMsg.getRole())
                            .content(accumulatedContent)
                            .build();
            if (context.getChatUsage() != null) {
                accumulated
                        .getMetadata()
                        .put(MessageMetadataKeys.CHAT_USAGE, context.getChatUsage());
            }
            ReasoningChunkEvent event =
                    new ReasoningChunkEvent(
                            this, model.getModelName(), null, chunkMsg, accumulated);
            return Flux.fromIterable(getSortedHooks()).flatMap(hook -> hook.onEvent(event)).then();
        }

        return Mono.empty();
    }

    // ==================== 摘要钩子通知方法 ====================

    private Mono<PreSummaryEvent> notifyPreSummaryHook(
            List<Msg> msgs, GenerateOptions generateOptions) {
        PreSummaryEvent event =
                new PreSummaryEvent(
                        this, model.getModelName(), generateOptions, msgs, maxIters, maxIters);
        event.setSystemMessage(currentSystemMsg.get());
        return notifyHooks(event);
    }

    private Mono<PostSummaryEvent> notifyPostSummaryHook(Msg msg, GenerateOptions generateOptions) {
        return notifyHooks(new PostSummaryEvent(this, model.getModelName(), generateOptions, msg));
    }

    private Mono<Void> notifySummaryChunk(
            Msg chunkMsg, ReasoningContext context, GenerateOptions generateOptions) {
        ContentBlock content = chunkMsg.getFirstContentBlock();

        ContentBlock accumulatedContent = null;
        if (content instanceof TextBlock) {
            accumulatedContent = TextBlock.builder().text(context.getAccumulatedText()).build();
        } else if (content instanceof ThinkingBlock) {
            accumulatedContent =
                    ThinkingBlock.builder().thinking(context.getAccumulatedThinking()).build();
        }

        if (accumulatedContent != null) {
            Msg accumulated =
                    Msg.builder()
                            .id(chunkMsg.getId())
                            .name(chunkMsg.getName())
                            .role(chunkMsg.getRole())
                            .content(accumulatedContent)
                            .build();
            if (context.getChatUsage() != null) {
                accumulated
                        .getMetadata()
                        .put(MessageMetadataKeys.CHAT_USAGE, context.getChatUsage());
            }
            SummaryChunkEvent event =
                    new SummaryChunkEvent(
                            this, model.getModelName(), generateOptions, chunkMsg, accumulated);
            return Flux.fromIterable(getSortedHooks()).flatMap(hook -> hook.onEvent(event)).then();
        }

        return Mono.empty();
    }

    @Override
    protected Mono<Msg> handleInterrupt(InterruptContext context, Msg... originalArgs) {
        if (context.getSource() == InterruptSource.SYSTEM) {
            shutdownManager.saveOnInterruptObserved(this);
            return Mono.error(new AgentShuttingDownException());
        }

        String recoveryText = "I noticed that you have interrupted me. What can I do for you?";

        Msg recoveryMsg =
                Msg.builder()
                        .name(getName())
                        .role(MsgRole.ASSISTANT)
                        .content(TextBlock.builder().text(recoveryText).build())
                        .build();

        memory.addMessage(recoveryMsg);
        return Mono.just(recoveryMsg);
    }

    @Override
    protected Mono<Void> doObserve(Msg msg) {
        if (msg != null) {
            memory.addMessage(msg);
        }
        return Mono.empty();
    }

    // ==================== Getter 方法 ====================

    @Override
    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        throw new UnsupportedOperationException(
                "智能体构建后无法替换记忆。"
                        + "如果需要不同的记忆，请创建新的智能体实例。");
    }

    public String getSysPrompt() {
        return sysPrompt;
    }

    public Model getModel() {
        return model;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public PlanNotebook getPlanNotebook() {
        return planNotebook;
    }

    /**
     * 获取此智能体配置的生成选项。
     *
     * @return 生成选项，如果未配置则返回 null
     */
    public GenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==================== 构建器 ====================

    public static class Builder {
        private String name;
        private String description;
        private String sysPrompt;
        private boolean checkRunning = true;
        private Model model;
        private Toolkit toolkit = new Toolkit();
        private Memory memory = new InMemoryMemory();
        private int maxIters = 10;
        private ExecutionConfig modelExecutionConfig;
        private ExecutionConfig toolExecutionConfig;
        private GenerateOptions generateOptions;
        private final Set<Hook> hooks = new LinkedHashSet<>();
        private boolean enableMetaTool = false;
        private StructuredOutputReminder structuredOutputReminder =
                StructuredOutputReminder.TOOL_CHOICE;
        private PlanNotebook planNotebook;
        private SkillBox skillBox;
        private ToolExecutionContext toolExecutionContext;
        private boolean enablePendingToolRecovery = false;

        // Long-term memory configuration
        private LongTermMemory longTermMemory;
        private LongTermMemoryMode longTermMemoryMode = LongTermMemoryMode.BOTH;
        private boolean longTermMemoryAsyncRecord = false;

        // State persistence configuration
        private StatePersistence statePersistence;

        // RAG configuration
        private final Set<Knowledge> knowledgeBases = new LinkedHashSet<>();
        private RAGMode ragMode = RAGMode.GENERIC;
        private RetrieveConfig retrieveConfig =
                RetrieveConfig.builder().limit(5).scoreThreshold(0.5).build();

        private Builder() {}

        /**
         * 设置此智能体的名称。
         *
         * @param name 智能体名称，不能为 null
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder checkRunning(boolean checkRunning) {
            this.checkRunning = checkRunning;
            return this;
        }

        /**
         * 设置此智能体的系统提示词。
         *
         * @param sysPrompt 系统提示词，可以为 null 或空
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder sysPrompt(String sysPrompt) {
            this.sysPrompt = sysPrompt;
            return this;
        }

        /**
         * 设置此智能体使用的语言模型。
         *
         * @param model 用于推理的语言模型，不能为 null
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        /**
         * 设置包含此智能体可用工具的工具包。
         *
         * @param toolkit 包含可用工具的工具包，不能为 null
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder toolkit(Toolkit toolkit) {
            this.toolkit = toolkit;
            return this;
        }

        /**
         * 设置用于存储对话历史的记忆。
         *
         * @param memory 记忆实现，可以为 null（默认为 InMemoryMemory）
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder memory(Memory memory) {
            this.memory = memory;
            return this;
        }

        /**
         * 设置推理-行动迭代的最大次数。
         *
         * @param maxIters 最大迭代次数，必须为正数
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder maxIters(int maxIters) {
            this.maxIters = maxIters;
            return this;
        }

        /**
         * 添加用于监控和拦截智能体执行事件的钩子。
         *
         * <p>钩子可以观察或修改推理、行动和其他阶段的事件。
         * 可以添加多个钩子，它们将按优先级顺序执行（较低的优先级值先执行）。
         *
         * @param hook 要添加的钩子，不能为 null
         * @return 此构建器实例，用于方法链式调用
         * @see Hook
         * @see Hook#tools()
         */
        public Builder hook(Hook hook) {
            this.hooks.add(hook);
            return this;
        }

        /**
         * 添加多个用于监控和拦截智能体执行事件的钩子。
         *
         * <p>钩子可以观察或修改推理、行动和其他阶段的事件。
         * 所有钩子将按优先级顺序执行（较低的优先级值先执行）。
         *
         * @param hooks 要添加的钩子列表，不能为 null
         * @return 此构建器实例，用于方法链式调用
         * @see Hook
         * @see Hook#tools()
         */
        public Builder hooks(List<Hook> hooks) {
            this.hooks.addAll(hooks);
            return this;
        }

        /**
         * 启用或禁用元工具功能。
         *
         * <p>启用时，工具包将自动注册一个元工具，向智能体提供有关可用工具的信息。
         * 这可以帮助智能体了解有哪些工具可用，而不仅仅依赖系统提示词。
         *
         * @param enableMetaTool true 启用元工具，false 禁用
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder enableMetaTool(boolean enableMetaTool) {
            this.enableMetaTool = enableMetaTool;
            return this;
        }

        /**
         * 启用或禁用从孤立待处理工具调用的自动恢复。
         *
         * <p>启用时，会自动注册 {@link PendingToolRecoveryHook} 来检测并修补
         * 孤立的待处理工具调用，在智能体处理开始之前添加合成错误结果。
         * 这防止了在工具执行失败、超时或被中断时抛出 {@link IllegalStateException}。
         *
         * <p>如果您希望通过 HITL（人机协同）机制或自定义错误处理策略手动处理
         * 待处理工具调用，请禁用此功能。
         *
         * @param enable true 启用自动恢复，false 禁用
         * @return 此构建器实例，用于方法链式调用
         * @see PendingToolRecoveryHook
         */
        public Builder enablePendingToolRecovery(boolean enable) {
            this.enablePendingToolRecovery = enable;
            return this;
        }

        /**
         * 设置模型 API 调用的执行配置。
         *
         * <p>此配置控制推理阶段模型请求的超时、重试行为和退避策略。
         * 如果未设置，智能体将使用模型的默认执行配置。
         *
         * @param modelExecutionConfig 模型调用的执行配置，可以为 null
         * @return 此构建器实例，用于方法链式调用
         * @see ExecutionConfig
         */
        public Builder modelExecutionConfig(ExecutionConfig modelExecutionConfig) {
            this.modelExecutionConfig = modelExecutionConfig;
            return this;
        }

        /**
         * 设置工具执行的执行配置。
         *
         * <p>此配置控制行动阶段工具调用的超时、重试行为和退避策略。
         * 如果未设置，工具包将使用其默认执行配置。
         *
         * @param toolExecutionConfig 工具调用的执行配置，可以为 null
         * @return 此构建器实例，用于方法链式调用
         * @see ExecutionConfig
         */
        public Builder toolExecutionConfig(ExecutionConfig toolExecutionConfig) {
            this.toolExecutionConfig = toolExecutionConfig;
            return this;
        }

        /**
         * 设置模型 API 调用的生成选项。
         *
         * <p>此配置控制 LLM 生成参数，如 temperature、topP、maxTokens、
         * frequencyPenalty、presencePenalty 等。这些选项在推理阶段传递给模型。
         *
         * <p><b>使用示例：</b>
         * <pre>{@code
         * ReActAgent agent = ReActAgent.builder()
         *     .name("助手")
         *     .model(model)
         *     .generateOptions(GenerateOptions.builder()
         *         .temperature(0.7)
         *         .topP(0.9)
         *         .maxTokens(1000)
         *         .build())
         *     .build();
         * }</pre>
         *
         * <p><b>注意：</b>如果同时设置了 generateOptions 和 modelExecutionConfig，
         * modelExecutionConfig 的 executionConfig 将被合并到 generateOptions 中，
         * modelExecutionConfig 在执行设置方面具有优先权。
         *
         * @param generateOptions 模型调用的生成选项，可以为 null
         * @return 此构建器实例，用于方法链式调用
         * @see GenerateOptions
         */
        public Builder generateOptions(GenerateOptions generateOptions) {
            this.generateOptions = generateOptions;
            return this;
        }

        /**
         * 设置结构化输出强制模式。
         *
         * @param reminder 结构化输出提醒模式，不能为 null
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder structuredOutputReminder(StructuredOutputReminder reminder) {
            this.structuredOutputReminder = reminder;
            return this;
        }

        /**
         * 设置用于基于计划的任务执行的 PlanNotebook。
         *
         * <p>当提供时，PlanNotebook 将集成到智能体中：
         * <ul>
         *   <li>计划管理工具将自动注册到工具包</li>
         *   <li>将添加一个钩子，在每个推理步骤之前注入计划提示</li>
         * </ul>
         *
         * @param planNotebook 已配置的 PlanNotebook 实例，可以为 null
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder planNotebook(PlanNotebook planNotebook) {
            this.planNotebook = planNotebook;
            return this;
        }

        /**
         * 设置此智能体的技能盒。
         *
         * <p>技能盒用于管理此智能体的技能。它将用于将技能注册到工具包中。
         * <ul>
         *   <li>技能加载工具将自动注册到工具包</li>
         *   <li>将添加一个技能钩子，在 {@link io.agentscope.core.hook.PreCallEvent} 时注入技能提示
         *       并管理技能激活</li>
         * </ul>
         * @param skillBox 用于此智能体的技能盒
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder skillBox(SkillBox skillBox) {
            this.skillBox = skillBox;
            return this;
        }

        /**
         * 设置此智能体的长期记忆。
         *
         * <p>长期记忆使智能体能够在会话之间记住信息。
         * 它可以与 {@link #longTermMemoryMode(LongTermMemoryMode)} 结合使用，
         * 以控制记忆管理是自动的、智能体控制的还是两者兼有。
         *
         * @param longTermMemory 长期记忆实现
         * @return 此构建器实例，用于方法链式调用
         * @see LongTermMemoryMode
         */
        public Builder longTermMemory(LongTermMemory longTermMemory) {
            this.longTermMemory = longTermMemory;
            return this;
        }

        /**
         * 设置长期记忆模式。
         *
         * <p>这决定了长期记忆如何与智能体集成：
         * <ul>
         *   <li><b>AGENT_CONTROL：</b>注册记忆工具供智能体调用</li>
         *   <li><b>STATIC_CONTROL：</b>框架自动检索/记录记忆</li>
         *   <li><b>BOTH：</b>结合两种方法（默认）</li>
         * </ul>
         *
         * @param mode 长期记忆模式
         * @return 此构建器实例，用于方法链式调用
         * @see LongTermMemoryMode
         */
        public Builder longTermMemoryMode(LongTermMemoryMode mode) {
            this.longTermMemoryMode = mode;
            return this;
        }

        /**
         * 设置长期记忆记录是否应异步执行。
         *
         * <p>启用时，框架将以即发即忘的方式将记忆记录到长期存储中，
         * 而不阻塞智能体的主执行流程。这提高了响应延迟，但意味着
         * 在智能体返回其响应之前不能保证记忆持久化。
         *
         * <p>禁用时（默认），框架会等待记录操作完成后再返回智能体的响应。
         * 这确保记忆持久化已最终确定，但可能会增加响应延迟。
         *
         * <p>注意：此设置仅影响静态控制模式（STATIC_CONTROL、BOTH）。
         * 通过工具进行的智能体控制记录始终是同步的。
         *
         * @param asyncRecord 是否异步记录记忆
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder longTermMemoryAsyncRecord(boolean asyncRecord) {
            this.longTermMemoryAsyncRecord = asyncRecord;
            return this;
        }

        /**
         * 设置状态持久化配置。
         *
         * <p>使用此配置可以控制在 saveTo/loadFrom 操作期间由智能体管理哪些组件的状态。
         * 默认情况下，所有组件都被管理。
         *
         * <p>使用示例：
         *
         * <pre>{@code
         * ReActAgent agent = ReActAgent.builder()
         *     .name("助手")
         *     .model(model)
         *     .statePersistence(StatePersistence.builder()
         *         .planNotebookManaged(false)  // 让用户单独管理 PlanNotebook
         *         .build())
         *     .build();
         * }</pre>
         *
         * @param statePersistence 状态持久化配置
         * @return 此构建器实例，用于方法链式调用
         * @see StatePersistence
         */
        public Builder statePersistence(StatePersistence statePersistence) {
            this.statePersistence = statePersistence;
            return this;
        }

        /**
         * 使用默认配置启用计划功能。
         *
         * <p>这是一个便捷方法，等效于：
         * <pre>{@code
         * planNotebook(PlanNotebook.builder().build())
         * }</pre>
         *
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder enablePlan() {
            this.planNotebook = PlanNotebook.builder().build();
            return this;
        }

        /**
         * 添加用于 RAG（检索增强生成）的知识库。
         *
         * @param knowledge 要添加的知识库
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder knowledge(Knowledge knowledge) {
            if (knowledge != null) {
                this.knowledgeBases.add(knowledge);
            }
            return this;
        }

        /**
         * 添加多个用于 RAG 的知识库。
         *
         * @param knowledges 要添加的知识库列表
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder knowledges(List<Knowledge> knowledges) {
            if (knowledges != null) {
                this.knowledgeBases.addAll(knowledges);
            }
            return this;
        }

        /**
         * 设置 RAG 模式。
         *
         * @param mode RAG 模式（GENERIC、AGENTIC 或 NONE）
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder ragMode(RAGMode mode) {
            if (mode != null) {
                this.ragMode = mode;
            }
            return this;
        }

        /**
         * 设置 RAG 的检索配置。
         *
         * @param config 检索配置
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder retrieveConfig(RetrieveConfig config) {
            if (config != null) {
                this.retrieveConfig = config;
            }
            return this;
        }

        /**
         * 设置此智能体的工具执行上下文。
         *
         * <p>此上下文将传递给此智能体调用的所有工具，可以包括用户身份、
         * 会话信息、权限和其他元数据。此智能体级别的上下文将覆盖
         * 工具包级别的上下文，但可以被调用级别的上下文覆盖。
         *
         * @param toolExecutionContext 工具执行上下文
         * @return 此构建器实例，用于方法链式调用
         */
        public Builder toolExecutionContext(ToolExecutionContext toolExecutionContext) {
            this.toolExecutionContext = toolExecutionContext;
            return this;
        }

        /**
         * 使用配置的设置构建并返回一个新的 ReActAgent 实例。
         *
         * @return 新的 ReActAgent 实例
         * @throws IllegalArgumentException 如果缺少必需参数或参数无效
         */
        public ReActAgent build() {
            // 深拷贝工具包以避免智能体之间的状态干扰
            Toolkit agentToolkit = this.toolkit.copy();

            registerToolsFromHooks(agentToolkit);

            if (enableMetaTool) {
                agentToolkit.registerMetaTool();
            }

            // 如果启用则注册 PendingToolRecoveryHook
            if (enablePendingToolRecovery) {
                hooks.add(new PendingToolRecoveryHook());
            }

            // 如果提供则配置长期记忆
            if (longTermMemory != null) {
                configureLongTermMemory(agentToolkit);
            }

            // 如果提供知识库则配置 RAG
            if (!knowledgeBases.isEmpty()) {
                configureRAG(agentToolkit);
            }

            // 如果提供则配置 PlanNotebook
            if (planNotebook != null) {
                configurePlan(agentToolkit);
            }

            // 如果提供则配置 SkillBox
            if (skillBox != null) {
                configureSkillBox(agentToolkit);
            }

            return new ReActAgent(this, agentToolkit);
        }

        /**
         * 在智能体工具包上注册由钩子（{@link Hook#tools()}）声明的工具对象。
         *
         * <p>在 {@link Toolkit#copy()} 之后运行，因此钩子提供的工具被限定到此智能体
         * 实例，而不会修改构建器的原始工具包。
         */
        private void registerToolsFromHooks(Toolkit agentToolkit) {
            for (Hook hook : hooks) {
                List<Object> toolObjects = hook.tools();
                if (toolObjects == null || toolObjects.isEmpty()) {
                    continue;
                }
                for (Object toolObject : toolObjects) {
                    if (toolObject != null) {
                        agentToolkit.registerTool(toolObject);
                    }
                }
            }
        }

        /**
         * 根据选择的模式配置长期记忆。
         *
         * <p>此方法设置长期记忆集成：
         * <ul>
         *   <li>AGENT_CONTROL：注册记忆工具供智能体调用</li>
         *   <li>STATIC_CONTROL：注册 StaticLongTermMemoryHook 用于自动检索/记录</li>
         *   <li>BOTH：结合两种方法（注册工具 + 钩子）</li>
         * </ul>
         */
        private void configureLongTermMemory(Toolkit agentToolkit) {
            // If agent control is enabled, register memory tools via adapter
            if (longTermMemoryMode == LongTermMemoryMode.AGENT_CONTROL
                    || longTermMemoryMode == LongTermMemoryMode.BOTH) {
                agentToolkit.registerTool(new LongTermMemoryTools(longTermMemory));
            }

            // If static control is enabled, register the hook for automatic memory management
            if (longTermMemoryMode == LongTermMemoryMode.STATIC_CONTROL
                    || longTermMemoryMode == LongTermMemoryMode.BOTH) {
                StaticLongTermMemoryHook hook =
                        new StaticLongTermMemoryHook(
                                longTermMemory, memory, longTermMemoryAsyncRecord);
                hooks.add(hook);
            }
        }

        /**
         * 根据选择的模式配置 RAG（检索增强生成）。
         *
         * <p>此方法根据 RAG 模式自动设置适当的钩子或工具：
         * <ul>
         *   <li>GENERIC：添加 GenericRAGHook 以自动注入知识</li>
         *   <li>AGENTIC：注册 KnowledgeRetrievalTools 供智能体控制检索</li>
         *   <li>NONE：不执行任何操作</li>
         * </ul>
         */
        private void configureRAG(Toolkit agentToolkit) {
            // 如果提供多个知识库则聚合
            Knowledge aggregatedKnowledge;
            if (knowledgeBases.size() == 1) {
                aggregatedKnowledge = knowledgeBases.iterator().next();
            } else {
                aggregatedKnowledge = buildAggregatedKnowledge();
            }

            // 根据模式配置
            switch (ragMode) {
                case GENERIC -> {
                    // 创建并添加 GenericRAGHook
                    GenericRAGHook ragHook =
                            new GenericRAGHook(aggregatedKnowledge, retrieveConfig);
                    hooks.add(ragHook);
                }
                case AGENTIC -> {
                    // 注册知识检索工具
                    KnowledgeRetrievalTools tools =
                            new KnowledgeRetrievalTools(aggregatedKnowledge, retrieveConfig);
                    agentToolkit.registerTool(tools);
                }
                case NONE -> {
                    // 不执行任何操作
                }
            }
        }

        private Knowledge buildAggregatedKnowledge() {
            return new Knowledge() {
                @Override
                public Mono<Void> addDocuments(List<Document> documents) {
                    return Flux.fromIterable(knowledgeBases)
                            .flatMap(kb -> kb.addDocuments(documents))
                            .then();
                }

                @Override
                public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
                    return Flux.fromIterable(knowledgeBases)
                            .flatMap(kb -> kb.retrieve(query, config))
                            .collectList()
                            .map(this::mergeAndSortResults);
                }

                private List<Document> mergeAndSortResults(List<List<Document>> allResults) {
                    return allResults.stream()
                            .flatMap(List::stream)
                            .collect(
                                    Collectors.toMap(
                                            Document::getId,
                                            doc -> doc,
                                            (doc1, doc2) ->
                                                    doc1.getScore() != null
                                                                    && doc2.getScore() != null
                                                                    && doc1.getScore()
                                                                            > doc2.getScore()
                                                            ? doc1
                                                            : doc2))
                            .values()
                            .stream()
                            .sorted(
                                    Comparator.comparing(
                                            Document::getScore,
                                            Comparator.nullsLast(Comparator.reverseOrder())))
                            .limit(retrieveConfig.getLimit())
                            .toList();
                }
            };
        }

        /**
         * 配置 PlanNotebook 集成。
         *
         * <p>此方法自动：
         * <ul>
         *   <li>将计划管理工具注册到工具包</li>
         *   <li>添加一个钩子，在每个推理步骤之前注入计划提示</li>
         * </ul>
         */
        private void configurePlan(Toolkit agentToolkit) {
            // 将计划工具注册到工具包
            agentToolkit.registerTool(planNotebook);

            // 添加计划提示钩子
            Hook planHintHook =
                    new Hook() {
                        @Override
                        public <T extends HookEvent> Mono<T> onEvent(T event) {
                            if (event instanceof PreReasoningEvent) {
                                PreReasoningEvent e = (PreReasoningEvent) event;
                                return planNotebook
                                        .getCurrentHint()
                                        .map(
                                                hintMsg -> {
                                                    List<Msg> modifiedMsgs =
                                                            new ArrayList<>(e.getInputMessages());
                                                    modifiedMsgs.add(hintMsg);
                                                    e.setInputMessages(modifiedMsgs);
                                                    return (T) e;
                                                })
                                        .defaultIfEmpty(event);
                            }
                            return Mono.just(event);
                        }
                    };

            hooks.add(planHintHook);
        }

        /**
         * 配置 SkillBox 集成。
         *
         * <p>此方法自动：
         * <ul>
         *   <li>将技能加载工具注册到工具包</li>
         *   <li>添加技能钩子，在 {@link io.agentscope.core.hook.PreCallEvent} 时注入技能提示
         *       （优先级为 {@link io.agentscope.core.skill.SkillHook#SKILL_HOOK_PRIORITY}）并管理技能激活</li>
         *   <li>如果启用自动上传，则将技能文件上传到上传目录</li>
         * </ul>
         */
        private void configureSkillBox(Toolkit agentToolkit) {
            skillBox.bindToolkit(agentToolkit);
            // 将技能加载工具注册到工具包
            skillBox.registerSkillLoadTool();

            // 如果启用自动上传，则上传技能文件
            if (skillBox.isAutoUploadSkill()) {
                skillBox.uploadSkillFiles();
            }

            hooks.add(new SkillHook(skillBox));
        }
    }
}
