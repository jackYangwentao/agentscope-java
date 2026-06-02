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
package io.agentscope.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.RuntimeContextAware;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.interruption.InterruptSource;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.shutdown.GracefulShutdownHook;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.tracing.TracerRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Abstract base class for all agents in the AgentScope framework.
 *
 * <p>AgentScope 框架中所有 Agent 的抽象基类。
 *
 * <p>本类为 Agent 提供通用功能,包括:基础 Hook 集成、MsgHub 订阅者管理、中断处理、
 * 链路追踪以及通过 {@link StateModule} 进行的 state 管理。它<b>不</b>负责内存管理
 * (Memory) — 那是具体 Agent 实现(如 ReActAgent)的责任。
 *
 * <p>设计理念:
 * <ul>
 *   <li>{@code AgentBase} 只提供基础设施(Hook、订阅、中断、state),不包含业务逻辑</li>
 *   <li>内存(Memory)管理委托给需要它的具体 Agent(如 ReActAgent)</li>
 *   <li>state 管理实现 {@link StateModule} 接口</li>
 *   <li>中断机制使用响应式模式:子类在合适的检查点调用 {@code checkInterruptedAsync()},
 *       将 {@link InterruptedException} 通过 Mono 链向上传播</li>
 *   <li>Observe 模式:Agent 可以在不回复的情况下接收消息</li>
 * </ul>
 *
 * <p><b>线程安全:</b>
 * Agent 实例<b>不</b>设计为并发执行。单个 Agent 实例不应被多个线程并发调用
 * (例如同时调用 {@code call()} 或 {@code stream()})。hooks 列表是可变的,在流式操作期间
 * 会被修改且未加同步,只有在单线程执行单 Agent 实例时才安全。
 *
 * <p><b>中断机制:</b>
 * <pre>{@code
 * // 外部调用中断
 * agent.interrupt(userMsg);
 *
 * // 在 Agent 的 Mono 链中,在检查点处:
 * return checkInterruptedAsync()
 *     .then(doWork())
 *     .flatMap(result -> checkInterruptedAsync().thenReturn(result));
 *
 * // AgentBase.call() 捕获异常:
 * .onErrorResume(error -> {
 *     if (error instanceof InterruptedException) {
 *         return handleInterrupt(context, msg);
 *     }
 *     ...
 * });
 * }</pre>
 *
 * <p>This class provides common functionality for agents including basic hook integration,
 * MsgHub subscriber management, interrupt handling, tracing, and state management through StateModule.
 * It does NOT manage memory - that is the responsibility of specific agent implementations like
 * ReActAgent.
 *
 * <p>Design Philosophy:
 * <ul>
 *   <li>AgentBase provides infrastructure (hooks, subscriptions, interrupt, state) but not domain
 *       logic</li>
 *   <li>Memory management is delegated to concrete agents that need it (e.g., ReActAgent)</li>
 *   <li>State management implements StateModule interface</li>
 *   <li>Interrupt mechanism uses reactive patterns: subclasses call checkInterruptedAsync()
 *       at appropriate checkpoints, which propagates InterruptedException through Mono chain</li>
 *   <li>Observe pattern: agents can receive messages without generating a reply</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b>
 * Agent instances are NOT designed for concurrent execution. A single agent instance should not
 * be invoked concurrently from multiple threads (e.g., calling {@code call()} or {@code stream()}
 * simultaneously). The hooks list is mutable and modified during streaming operations without
 * synchronization, which is safe only under single-threaded execution per agent instance.
 *
 * <p><b>Interrupt Mechanism:</b>
 * <pre>{@code
 * // External call to interrupt
 * agent.interrupt(userMsg);
 *
 * // Inside agent's Mono chain, at checkpoints:
 * return checkInterruptedAsync()
 *     .then(doWork())
 *     .flatMap(result -> checkInterruptedAsync().thenReturn(result));
 *
 * // AgentBase.call() catches the exception:
 * .onErrorResume(error -> {
 *     if (error instanceof InterruptedException) {
 *         return handleInterrupt(context, msg);
 *     }
 *     ...
 * });
 * }</pre>
 */
public abstract class AgentBase implements StateModule, Agent {

    private final String agentId;
    private final String name;
    private final String description;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final boolean checkRunning;
    private final List<Hook> hooks;
    private static final List<Hook> systemHooks =
            new CopyOnWriteArrayList<>(
                    List.of(new GracefulShutdownHook(GracefulShutdownManager.getInstance())));
    private final Map<String, List<AgentBase>> hubSubscribers = new ConcurrentHashMap<>();

    // 中断状态(所有 Agent 可用)
    private final AtomicBoolean interruptFlag = new AtomicBoolean(false);
    private final AtomicReference<Msg> userInterruptMessage = new AtomicReference<>(null);
    // Hook 永远非 null
    private static final Comparator<Hook> HOOK_COMPARATOR = Comparator.comparingInt(Hook::priority);
    private final AtomicReference<InterruptSource> interruptSource =
            new AtomicReference<>(InterruptSource.USER);

    private final CopyOnWriteArrayList<RuntimeContextAware> runtimeContextAwareHooks =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<RuntimeContext> currentRuntimeContext = new AtomicReference<>();

    /**
     * 构造一个不带描述的 Agent。
     *
     * @param name Agent 名称
     */
    public AgentBase(String name) {
        this(name, null, true, List.of());
    }

    /**
     * 构造一个带描述的 Agent。
     *
     * @param name Agent 名称
     * @param description Agent 描述
     */
    public AgentBase(String name, String description) {
        this(name, description, true, List.of());
    }

    /**
     * 完整构造器。
     *
     * @param name Agent 名称
     * @param description Agent 描述
     * @param checkRunning 是否检查运行状态(防止同一实例并发执行)
     * @param hooks 用于监控/拦截执行的 Hook 列表
     */
    public AgentBase(String name, String description, boolean checkRunning, List<Hook> hooks) {
        this.agentId = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.checkRunning = checkRunning;
        this.hooks = new CopyOnWriteArrayList<>(hooks != null ? hooks : List.of());
        this.hooks.addAll(systemHooks);
        sortHooks();
        for (Hook h : this.hooks) {
            registerRuntimeContextHookIfNeeded(h);
        }
    }

    @Override
    public final String getAgentId() {
        return agentId;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String getDescription() {
        return description != null ? description : Agent.super.getDescription();
    }

    /**
     * 处理输入消息列表并生成响应,在执行过程中触发 Hook。
     *
     * <p>启用 telemetry 后会自动捕获追踪数据。
     *
     * @param msgs 输入消息列表
     * @return 响应消息
     */
    @Override
    public final Mono<Msg> call(List<Msg> msgs) {
        return Mono.using(
                this::acquireExecution,
                resource -> {
                    beforeAgentExecution(msgs);
                    return TracerRegistry.get()
                            .callAgent(
                                    this,
                                    msgs,
                                    () ->
                                            notifyPreCall(msgs)
                                                    .flatMap(this::doCall)
                                                    .flatMap(this::notifyPostCall)
                                                    .onErrorResume(
                                                            createErrorHandler(
                                                                    msgs.toArray(new Msg[0]))));
                },
                this::releaseExecution,
                true);
    }

    /**
     * 处理输入消息列表并生成结构化输出,在执行过程中触发 Hook。
     *
     * <p>启用 telemetry 后会自动捕获追踪数据。
     *
     * @param msgs 输入消息列表
     * @param structuredOutputClass 定义输出结构的类
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    @Override
    public final Mono<Msg> call(List<Msg> msgs, Class<?> structuredOutputClass) {
        return Mono.using(
                this::acquireExecution,
                resource -> {
                    beforeAgentExecution(msgs);
                    return TracerRegistry.get()
                            .callAgent(
                                    this,
                                    msgs,
                                    () ->
                                            notifyPreCall(msgs)
                                                    .flatMap(m -> doCall(m, structuredOutputClass))
                                                    .flatMap(this::notifyPostCall)
                                                    .onErrorResume(
                                                            createErrorHandler(
                                                                    msgs.toArray(new Msg[0]))));
                },
                this::releaseExecution,
                true);
    }

    /**
     * 处理输入消息列表并生成结构化输出(JSON Schema 模式),在执行过程中触发 Hook。
     *
     * <p>启用 telemetry 后会自动捕获追踪数据。
     *
     * @param msgs 输入消息列表
     * @param schema com.fasterxml.jackson.databind.JsonNode 实例,定义输出结构
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    @Override
    public final Mono<Msg> call(List<Msg> msgs, JsonNode schema) {
        return Mono.using(
                this::acquireExecution,
                resource -> {
                    beforeAgentExecution(msgs);
                    return TracerRegistry.get()
                            .callAgent(
                                    this,
                                    msgs,
                                    () ->
                                            notifyPreCall(msgs)
                                                    .flatMap(m -> doCall(m, schema))
                                                    .flatMap(this::notifyPostCall)
                                                    .onErrorResume(
                                                            createErrorHandler(
                                                                    msgs.toArray(new Msg[0]))));
                },
                this::releaseExecution,
                true);
    }

    /**
     * 处理多条输入消息的内部实现。子类必须在此实现其特定逻辑。
     *
     * @param msgs 输入消息列表
     * @return 响应消息
     */
    protected abstract Mono<Msg> doCall(List<Msg> msgs);

    /**
     * 处理多条输入消息并生成结构化输出的内部实现。支持结构化输出的子类需重写本方法。
     * 默认实现抛出 {@link UnsupportedOperationException}。
     *
     * @param msgs 输入消息列表
     * @param structuredOutputClass 定义输出结构的类
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    protected Mono<Msg> doCall(List<Msg> msgs, Class<?> structuredOutputClass) {
        return Mono.error(
                new UnsupportedOperationException(
                        "Structured output not supported by " + getClass().getSimpleName()));
    }

    /**
     * 处理多条输入消息并生成结构化输出(JSON Schema 模式)的内部实现。
     * 支持结构化输出的子类需重写本方法。默认实现抛出 {@link UnsupportedOperationException}。
     *
     * @param msgs 输入消息列表
     * @param outputSchema com.fasterxml.jackson.databind.JsonNode 实例,定义输出结构
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    protected Mono<Msg> doCall(List<Msg> msgs, JsonNode outputSchema) {
        return Mono.error(
                new UnsupportedOperationException(
                        "Structured output not supported by " + outputSchema.asText()));
    }

    /**
     * 向所有 Agent 实例注册一个系统级 Hook。系统 Hook 会附加到每个新创建的 AgentBase 实例上。
     *
     * @param hook 要注册的系统 Hook
     */
    public static void addSystemHook(Hook hook) {
        systemHooks.add(hook);
    }

    /**
     * 从系统级 Hook 列表中移除一个 Hook。
     *
     * @param hook 要移除的系统 Hook
     */
    public static void removeSystemHook(Hook hook) {
        systemHooks.remove(hook);
    }

    /**
     * 中断当前 Agent 的执行。设置一个中断标志,Agent 会在适当的检查点检查它。
     */
    @Override
    public void interrupt() {
        interruptSource.set(InterruptSource.USER);
        interruptFlag.set(true);
    }

    /**
     * 中断当前 Agent 的执行并附带一条用户消息。设置中断标志,并将用户消息与中断关联。
     *
     * @param msg 与中断关联的用户消息
     */
    @Override
    public void interrupt(Msg msg) {
        interruptSource.set(InterruptSource.USER);
        interruptFlag.set(true);
        if (msg != null) {
            userInterruptMessage.set(msg);
        }
    }

    /**
     * 使用显式来源中断执行。
     *
     * @param source 中断来源
     */
    public void interrupt(InterruptSource source) {
        interruptSource.set(source != null ? source : InterruptSource.SYSTEM);
        interruptFlag.set(true);
    }

    /**
     * 检查 Agent 执行是否已被中断(响应式版本)。
     * 若未被中断则返回一个正常完成的 Mono;若已中断则 onError 抛出 {@link InterruptedException}。
     *
     * <p>子类应在其 Mono 链的适当检查点调用本方法。
     * 对于简单 Agent(如 {@code UserAgent}),可不需要检查点;
     * 对于复杂 Agent(如 {@code ReActAgent}),应在以下位置调用:
     * <ul>
     *   <li>每次迭代开始时</li>
     *   <li>推理前后</li>
     *   <li>每次工具执行前后</li>
     *   <li>流式传输期间(每个分片)</li>
     * </ul>
     *
     * <p>使用示例:
     * <pre>{@code
     * return checkInterruptedAsync()
     *     .then(reasoning())
     *     .flatMap(result -> checkInterruptedAsync().thenReturn(result))
     *     .flatMap(result -> executeTools(result));
     * }</pre>
     *
     * @return 未中断时正常完成,已中断时 onError
     */
    protected Mono<Void> checkInterruptedAsync() {
        return Mono.defer(
                () ->
                        interruptFlag.get()
                                ? Mono.error(
                                        new InterruptedException("Agent execution interrupted"))
                                : Mono.empty());
    }

    /**
     * 重置中断标志及其关联状态。在每次 {@code call()} 开始时调用,以准备新一次执行。
     */
    protected void resetInterruptFlag() {
        interruptFlag.set(false);
        userInterruptMessage.set(null);
        interruptSource.set(InterruptSource.USER);
    }

    /**
     * 从当前中断状态构造中断上下文。辅助方法,用于避免代码重复。
     *
     * @return 包含当前用户消息的 {@link InterruptContext}
     */
    private InterruptContext createInterruptContext() {
        return InterruptContext.builder()
                .source(interruptSource.get())
                .userMessage(userInterruptMessage.get())
                .build();
    }

    /**
     * 为一次 {@code call()} 获取执行资源。用于 {@link Mono#using} 的 resourceSupplier,
     * 保证无论成功、错误或取消,都会调用 {@link #releaseExecution}。
     *
     * @return 当前 Agent 实例
     */
    private AgentBase acquireExecution() {
        if (checkRunning && !running.compareAndSet(false, true)) {
            throw new IllegalStateException("Agent is still running, please wait for it to finish");
        }
        try {
            resetInterruptFlag();
            GracefulShutdownManager.getInstance().ensureAcceptingRequests();
            GracefulShutdownManager.getInstance().registerRequest(this);
        } catch (RuntimeException ex) {
            if (checkRunning) {
                running.set(false);
            }
            throw ex;
        }
        return this;
    }

    /**
     * 释放 {@code call()} 调用的执行资源。用于 {@link Mono#using} 的 resourceCleanup,
     * 无论响应式链如何终止(成功、错误或取消)都会被调用。
     *
     * @param resource Agent 实例(忽略,使用 {@code this})
     */
    private void releaseExecution(AgentBase resource) {
        afterAgentExecution();
        running.set(false);
        GracefulShutdownManager.getInstance().unregisterRequest(this);
    }

    /**
     * 为 call() 方法构造错误处理器。特殊处理 {@link InterruptedException}
     * 并委托给 {@code handleInterrupt},而其他错误则通过 Hook 通知。
     *
     * @param originalArgs 传递给 handleInterrupt 的原始参数
     * @return 适当地处理错误的 Function
     */
    private Function<Throwable, Mono<Msg>> createErrorHandler(Msg... originalArgs) {
        return error -> {
            if (error instanceof InterruptedException
                    || (error.getCause() instanceof InterruptedException)) {
                return handleInterrupt(createInterruptContext(), originalArgs);
            }
            return notifyError(error).then(Mono.error(error));
        };
    }

    /**
     * 获取中断标志,供子类访问。
     * 子类可用此标志在标准 checkInterruptedAsync() 方法之外实现自定义中断检查逻辑。
     *
     * @return 原子布尔中断标志
     *
     * <p>Get the interrupt flag for access by subclasses.
     * Subclasses can use this flag to implement custom interrupt-checking logic
     * in addition to the standard checkInterruptedAsync() method.
     *
     * @return The atomic boolean interrupt flag
     */
    protected AtomicBoolean getInterruptFlag() {
        return interruptFlag;
    }

    /**
     * 获取当前中断来源。
     *
     * @return 中断来源
     */
    protected InterruptSource getInterruptSource() {
        return interruptSource.get();
    }

    /**
     * 观察一条消息而不产生回复。让 Agent 接收来自其他 Agent 或环境的消息而不响应。
     * 常用于多 Agent 协作场景。
     *
     * <p>常见实现模式:
     * <ul>
     *   <li>无状态 Agent:不需要观察时直接空实现</li>
     *   <li>有状态 Agent:将消息存入 memory/context,以便后续调用使用</li>
     *   <li>协作 Agent:更新共享知识或触发副作用</li>
     * </ul>
     *
     * @param msg 要观察的消息
     * @return 观察完成时结束的 Mono
     */
    protected Mono<Void> doObserve(Msg msg) {
        return Mono.empty();
    }

    /**
     * 处理执行过程中发生的中断。子类必须实现本方法以提供基于中断上下文的恢复逻辑。
     *
     * <p>实现指引:
     * <ul>
     *   <li>简单 Agent:返回一条基本的中断确认消息</li>
     *   <li>复杂 Agent:生成包含待处理操作或部分结果的摘要</li>
     *   <li>有状态 Agent:确保返回前已正确保存 state</li>
     * </ul>
     *
     * @param context 包含中断元数据的中断上下文
     * @param originalArgs 调用 {@code call()} 时传入的原始参数(空、单条 Msg 或 List)
     * @return 返回给用户的恢复消息
     */
    protected abstract Mono<Msg> handleInterrupt(InterruptContext context, Msg... originalArgs);

    /**
     * 当前绑定到本 Agent 的 per-call {@link RuntimeContext}(例如 {@code ReActAgent}
     * 在 {@code call} 期间会绑定它)。
     */
    public RuntimeContext getRuntimeContext() {
        return currentRuntimeContext.get();
    }

    /**
     * 在 {@code call} / 基于流的 call 开始时调用,位于 {@link #acquireExecution} 之后、
     * 任何 Hook 之前。默认为 no-op。{@link io.agentscope.core.ReActAgent} 用本方法
     * 来绑定 {@link RuntimeContext}。
     */
    protected void beforeAgentExecution(List<Msg> msgs) {}

    /**
     * 在 {@code Mono.using} 的清理阶段调用,位于清除运行状态之前。与
     * {@link #beforeAgentExecution(List)} 配对,默认为 no-op。
     */
    protected void afterAgentExecution() {}

    /**
     * 将 {@code ctx} 绑定到本 Agent 引用及所有为本 Agent 注册的
     * {@link RuntimeContextAware} Hook。
     */
    protected void bindRuntimeContextToHooks(RuntimeContext ctx) {
        currentRuntimeContext.set(ctx);
        for (RuntimeContextAware h : runtimeContextAwareHooks) {
            h.setRuntimeContext(ctx);
        }
    }

    /**
     * 清除 {@link #getRuntimeContext()} 并将所有 {@link RuntimeContextAware} Hook 置为 null。
     */
    protected void unbindRuntimeContextFromHooks() {
        for (RuntimeContextAware h : runtimeContextAwareHooks) {
            h.setRuntimeContext(null);
        }
        currentRuntimeContext.set(null);
    }

    /**
     * 如果 Hook 实现了 RuntimeContextAware 接口则将其注册,以便在 per-call 绑定时自动注入 RuntimeContext。
     *
     * @param hook 要检查并注册的 Hook
     */
    private void registerRuntimeContextHookIfNeeded(Hook hook) {
        if (hook instanceof RuntimeContextAware r && !runtimeContextAwareHooks.contains(r)) {
            runtimeContextAwareHooks.add(r);
        }
    }

    /**
     * 获取本 Agent 的 Hook 列表。protected 以允许子类访问 Hook 实现自定义通知逻辑。
     *
     * @return Hook 列表
     */
    public List<Hook> getHooks() {
        return hooks;
    }

    /**
     * 动态向本 Agent 添加一个 Hook。
     *
     * <p>可在 Agent 执行过程中添加 Hook 以提供临时功能,常用于结构化输出处理
     * 或其他短期行为。
     *
     * @param hook 要添加的 Hook
     */
    protected void addHook(Hook hook) {
        if (hook != null) {
            hooks.add(hook);
            registerRuntimeContextHookIfNeeded(hook);
            sortHooks();
        }
    }

    /**
     * 按优先级对 Hook 列表排序,确保低优先级(数值小)的 Hook 先执行。
     */
    private void sortHooks() {
        this.hooks.sort(HOOK_COMPARATOR);
    }

    /**
     * 动态从本 Agent 移除一个 Hook。
     *
     * <p>当 Hook 不再需要时应移除,以避免内存泄漏和意外的副作用。
     *
     * @param hook 要移除的 Hook
     */
    protected void removeHook(Hook hook) {
        if (hook != null) {
            hooks.remove(hook);
            if (hook instanceof RuntimeContextAware r) {
                runtimeContextAwareHooks.remove(r);
            }
        }
    }

    /**
     * 按优先级排序获取 Hook 列表(数值越小,优先级越高)。
     * 相同优先级的 Hook 保持注册顺序。
     *
     * @return 排序后的 Hook 列表
     */
    protected List<Hook> getSortedHooks() {
        return hooks;
    }

    /**
     * 返回在 {@link PreCallEvent} 触发前要植入的初始系统消息。
     *
     * <p>默认实现返回 {@code null}。子类(如 {@code ReActAgent})重写本方法,
     * 以基于其配置的 {@code sysPrompt} 构造系统消息。
     *
     * @return 种子系统消息,没有则为 {@code null}
     */
    protected Msg seedSystemMsg() {
        return null;
    }

    /**
     * 在 {@link PreCallEvent} 钩子运行后被调用,接收最终的系统消息值。
     *
     * <p>默认实现为 no-op。子类(如 {@code ReActAgent})重写本方法,用于将系统消息
     * 持久化到 per-call 的 {@code AtomicReference},以便后续事件
     * ({@code PreReasoningEvent}、{@code PreSummaryEvent})可访问。
     *
     * @param systemMsg 由所有 PreCall Hook 产生的系统消息(可能为 null)
     */
    protected void consumeSystemMsgAfterPreCall(Msg systemMsg) {}

    /**
     * 通知所有 Hook:Agent 即将开始执行(preCall 钩子)。
     *
     * <p>事件的 {@code inputMessages} 包含完整的消息视图:
     * 先是 Agent 当前 memory 的快照,然后是调用 {@code call()} 时传入的 {@code callArgs}。
     * Hook 可在尾部追加非 SYSTEM 消息。禁止通过 {@code setInputMessages} 注入
     * {@link MsgRole#SYSTEM} 消息,在本方法末尾会进行检测 — 请改用
     * {@link PreCallEvent#setSystemMessage} 或 {@link PreCallEvent#appendSystemContent}。
     *
     * <p>Hook 运行后,系统消息通过 {@link #consumeSystemMsgAfterPreCall(Msg)} 交接,
     * 只有尾部消息(快照边界之后的)会返回,供 {@code doCall} 添加到 memory。
     *
     * @param callArgs 调用 {@code call()} 时传入的消息
     * @return 包含需由 {@code doCall} 添加到 memory 的新尾部消息的 Mono
     */
    private Mono<List<Msg>> notifyPreCall(List<Msg> callArgs) {
        // 在 Hook 运行前获取 memory 快照(Hook 前视图)
        // Take a memory snapshot before hooks run (pre-hook view)
        List<Msg> snapshot = List.of();
        if (this instanceof io.agentscope.core.ReActAgent reactAgent) {
            Memory mem = reactAgent.getMemory();
            if (mem != null) {
                snapshot = mem.getMessages();
            }
        }
        final int snapshotSize = snapshot.size();

        // 构造 Hook 的完整输入:快照 + callArgs
        // Build full input for hooks: snapshot + callArgs
        List<Msg> fullInput = new ArrayList<>(snapshot);
        if (callArgs != null) {
            fullInput.addAll(callArgs);
        }

        PreCallEvent event = new PreCallEvent(this, fullInput);
        event.setSystemMessage(seedSystemMsg());

        Mono<PreCallEvent> result = Mono.just(event);
        for (Hook hook : getSortedHooks()) {
            result = result.flatMap(hook::onEvent);
        }

        return result.map(
                e -> {
                    // 将系统消息交给 per-call 状态管理
                    // Hand off the system message to the per-call state
                    consumeSystemMsgAfterPreCall(e.getSystemMessage());

                    // 提取尾部:快照边界之后追加的消息
                    // Extract the tail: messages appended beyond the snapshot boundary
                    List<Msg> currentInput = e.getInputMessages();
                    List<Msg> tail;
                    if (currentInput == null || currentInput.size() <= snapshotSize) {
                        tail = List.of();
                    } else {
                        tail =
                                new ArrayList<>(
                                        currentInput.subList(snapshotSize, currentInput.size()));
                    }

                    // 守卫(仅 ReActAgent):Hook 不得向尾部注入 SYSTEM 消息,
                    // 因为尾部会被持久化到 memory,SYSTEM 消息会不断累积。
                    // 无 memory 的 Agent(如 UserAgent)可以合法地将 SYSTEM 消息作为 call 参数传入。
                    // Guard (ReActAgent only): hooks must not inject SYSTEM messages into the
                    // tail, since the tail is persisted to memory and SYSTEM messages would
                    // accumulate. Agents without memory (e.g. UserAgent) may legitimately
                    // pass SYSTEM messages as call arguments.
                    if (AgentBase.this instanceof io.agentscope.core.ReActAgent) {
                        for (Msg msg : tail) {
                            if (msg != null && msg.getRole() == MsgRole.SYSTEM) {
                                throw new IllegalStateException(
                                        "Hooks must not inject SYSTEM messages into"
                                                + " PreCallEvent.inputMessages. Use"
                                                + " event.setSystemMessage() or"
                                                + " event.appendSystemContent() instead.");
                            }
                        }
                    }

                    return tail;
                });
    }

    /**
     * 通知所有 Hook 执行已完成(postCall 钩子)。
     * Hook 通知完成后,将消息广播给所有订阅者。
     *
     * <p>Notify all hooks about completion (postCall hook).
     * After hook notification, broadcasts the message to all subscribers.
     *
     * @param finalMsg 最终消息
     * @return 包含可能被 Hook 修改后的最终消息的 Mono
     */
    private Mono<Msg> notifyPostCall(Msg finalMsg) {
        if (finalMsg == null) {
            return Mono.error(new IllegalStateException("Agent returned null message"));
        }
        PostCallEvent event = new PostCallEvent(this, finalMsg);
        Mono<PostCallEvent> result = Mono.just(event);
        for (Hook hook : getSortedHooks()) {
            result = result.flatMap(hook::onEvent);
        }
        // Hook 通知完成后,广播给订阅者
        // After hooks, broadcast to subscribers
        return result.map(PostCallEvent::getFinalMessage)
                .flatMap(msg -> broadcastToSubscribers(msg).thenReturn(msg));
    }

    /**
     * 通知所有 Hook 发生了错误。
     *
     * <p>Notify all hooks about error.
     *
     * @param error 发生的错误
     * @return 所有 Hook 通知完毕后完成的 Mono
     */
    private Mono<Void> notifyError(Throwable error) {
        ErrorEvent event = new ErrorEvent(this, error);
        return Flux.fromIterable(getSortedHooks()).flatMap(hook -> hook.onEvent(event)).then();
    }

    /**
     * 移除指定 MsgHub 的所有订阅者。
     * 通常在 MsgHub 被销毁或重置时调用。
     * 调用后,Agent 将不再从该 Hub 接收消息。
     *
     * <p>Remove all subscribers for a specific MsgHub.
     * This method is typically called when a MsgHub is being destroyed or reset.
     * After calling this method, the agent will no longer receive messages from the specified hub.
     *
     * @param hubId MsgHub 标识符
     */
    public void removeSubscribers(String hubId) {
        hubSubscribers.remove(hubId);
    }

    /**
     * 重置指定 MsgHub 的订阅者列表。
     * 用新的订阅者列表替换该 Hub 的现有订阅者。
     * 通常在订阅拓扑变化时由 MsgHub 调用。
     *
     * <p>Reset the subscriber list for a specific MsgHub.
     * This replaces any existing subscribers for the given hub with the new list.
     * Typically called by MsgHub when the subscription topology changes.
     *
     * @param hubId MsgHub 标识符
     * @param subscribers 新的订阅者列表(会被拷贝)
     */
    public void resetSubscribers(String hubId, List<AgentBase> subscribers) {
        hubSubscribers.put(hubId, new ArrayList<>(subscribers));
    }

    /**
     * 检查此 Agent 是否有任何订阅者。
     * 订阅者是会通过 MsgHub 接收本 Agent 发布的消息的其他 Agent。
     *
     * <p>Check if this agent has any subscribers.
     * Subscribers are agents that will receive messages published through MsgHub.
     *
     * @return 若有一个或多个订阅者则返回 true
     */
    public boolean hasSubscribers() {
        return !hubSubscribers.isEmpty()
                && hubSubscribers.values().stream().anyMatch(list -> !list.isEmpty());
    }

    /**
     * 获取所有 MsgHub 的订阅者总数。
     * 订阅者是会通过 MsgHub 接收本 Agent 发布的消息的其他 Agent。
     *
     * <p>Get the total number of subscribers across all MsgHubs.
     * Subscribers are agents that will receive messages published through MsgHub.
     *
     * @return 订阅者总数
     */
    public int getSubscriberCount() {
        return hubSubscribers.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 向所有 MsgHub 下的所有订阅者广播一条消息。
     * 每次 Agent 调用后自动调用,以实现 MsgHub 自动广播功能。
     *
     * <p>Broadcast a message to all subscribers across all MsgHubs.
     * This method is called automatically after each agent call to implement
     * the MsgHub auto-broadcast functionality.
     *
     * @param msg 要广播的消息
     * @return 所有订阅者都 observe 完消息后完成的 Mono
     */
    private Mono<Void> broadcastToSubscribers(Msg msg) {
        if (hubSubscribers.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(hubSubscribers.values())
                .flatMap(Flux::fromIterable)
                .flatMap(subscriber -> subscriber.observe(msg))
                .then();
    }

    /**
     * 观察单条消息而不产生回复。
     * 这是委派给 doObserve 实现的公开 API。
     *
     * <p>Observe a single message without generating a reply.
     * This is the public API that delegates to doObserve implementation.
     *
     * @param msg 要观察的消息
     * @return 观察完成后完成的 Mono
     */
    @Override
    public final Mono<Void> observe(Msg msg) {
        return doObserve(msg);
    }

    /**
     * 观察多条消息而不产生回复。
     * 这是委派给 doObserve 实现的公开 API,依次观察每条消息。
     *
     * <p>Observe multiple messages without generating a reply.
     * This is the public API that delegates to doObserve implementation.
     *
     * @param msgs 要观察的消息列表
     * @return 所有消息观察完成后完成的 Mono
     */
    @Override
    public final Mono<Void> observe(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(msgs).flatMap(this::doObserve).then();
    }

    /**
     * 以多条消息流式执行。
     *
     * <p>Stream with multiple input messages.
     *
     * @param msgs 输入消息
     * @param options 流配置选项
     * @return 执行期间发出的事件 Flux
     */
    @Override
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options) {
        return createEventStream(options, () -> call(msgs));
    }

    /**
     * 以多条消息流式执行,指定结构化输出类型。
     *
     * <p>Stream with multiple input messages and structured output type.
     *
     * @param msgs 输入消息
     * @param options 流配置选项
     * @param structuredModel 定义输出结构的可选用类
     * @return 执行期间发出的事件 Flux
     */
    @Override
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel) {
        return createEventStream(options, () -> call(msgs, structuredModel));
    }

    /**
     * 以多条消息流式执行,使用 JSON Schema 定义输出结构。
     *
     * <p>Stream with multiple input messages using a JSON schema.
     *
     * @param msgs 输入消息
     * @param options 流配置选项
     * @param schema 定义响应结构的 JSON Schema
     * @return 执行期间发出的事件 Flux
     */
    @Override
    public Flux<Event> stream(List<Msg> msgs, StreamOptions options, JsonNode schema) {
        return createEventStream(options, () -> call(msgs, schema));
    }

    /**
     * 创建事件流的辅助方法,管理 Hook 生命周期。
     *
     * <p>本方法处理 Agent 执行期间流式事件通知的通用逻辑,包括:
     * <ul>
     *   <li>创建并注册临时 StreamingHook</li>
     *   <li>管理 Hook 生命周期(从 Hook 列表添加/移除)</li>
     *   <li>可选地将最终 Agent 结果作为事件发出</li>
     *   <li>正确传播错误和完成信号</li>
     * </ul>
     *
     * <p>Helper method to create an event stream with proper hook lifecycle management.
     *
     * <p>This method handles the common logic for streaming events during agent execution,
     * including:
     * <ul>
     *   <li>Creating and registering a temporary StreamingHook</li>
     *   <li>Managing the hook lifecycle (add/remove from hooks list)</li>
     *   <li>Optionally emitting the final agent result as an event</li>
     *   <li>Properly propagating errors and completion signals</li>
     * </ul>
     *
     * @param options 流配置选项
     * @param callSupplier 执行 Agent 调用的 Supplier(单消息或消息列表)
     * @return 执行期间发出的事件 Flux
     */
    private Flux<Event> createEventStream(StreamOptions options, Supplier<Mono<Msg>> callSupplier) {
        return Flux.deferContextual(
                ctxView ->
                        Flux.<Event>create(
                                        sink -> {
                                            // 创建流式 Hook,绑定 sink 和选项
                                            // Create streaming hook with options
                                            StreamingHook streamingHook =
                                                    new StreamingHook(sink, options);

                                            // 注册临时 Hook
                                            // Add temporary hook
                                            addHook(streamingHook);

                                            // 子 Agent 工具使用的总线,将子事件推入父 sink,
                                            // 无需额外的 Flux 层。
                                            // Bus that subagent tools use to push child events
                                            // into this parent sink without an extra Flux layer.
                                            SubagentEventBus bus = sink::next;

                                            // 使用 Mono.defer 确保 trace 上下文传播,
                                            // 同时保持流式 Hook 功能
                                            // Use Mono.defer to ensure trace context propagation
                                            // while maintaining streaming hook functionality
                                            Mono.defer(() -> callSupplier.get())
                                                    .contextWrite(
                                                            context ->
                                                                    context.put(
                                                                                    SubagentEventBus
                                                                                            .CONTEXT_KEY,
                                                                                    bus)
                                                                            .putAll(ctxView))
                                                    .doFinally(
                                                            signalType -> {
                                                            // 移除临时 Hook
                                                            // Remove temporary hook
                                                                hooks.remove(streamingHook);
                                                            })
                                                    .subscribe(
                                                            finalMsg -> {
                                                                if (options.shouldStream(
                                                                        EventType.AGENT_RESULT)) {
                                                                    sink.next(
                                                                            new Event(
                                                                                    EventType
                                                                                            .AGENT_RESULT,
                                                                                    finalMsg,
                                                                                    true));
                                                                }

                                                                // 完成流
                                                                // Complete the stream
                                                                sink.complete();
                                                            },
                                                            sink::error);
                                        },
                                        FluxSink.OverflowStrategy.BUFFER)
                                .publishOn(Schedulers.boundedElastic()));
    }

    @Override
    public String toString() {
        return String.format("%s(id=%s, name=%s)", getClass().getSimpleName(), agentId, name);
    }
}
