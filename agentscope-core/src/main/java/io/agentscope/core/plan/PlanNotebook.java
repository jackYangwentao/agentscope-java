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
package io.agentscope.core.plan;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.plan.hint.DefaultPlanToHint;
import io.agentscope.core.plan.hint.PlanToHint;
import io.agentscope.core.plan.model.Plan;
import io.agentscope.core.plan.model.PlanState;
import io.agentscope.core.plan.model.SubTask;
import io.agentscope.core.plan.model.SubTaskState;
import io.agentscope.core.plan.storage.InMemoryPlanStorage;
import io.agentscope.core.plan.storage.PlanStorage;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.PlanNotebookState;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.StateModule;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 通过结构化计划管理复杂任务的计划笔记本。
 *
 * <p>为智能体提供创建、管理和跟踪计划的工具函数。通过基于钩子的机制自动注入上下文提示，
 * 引导智能体执行。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li><b>计划管理：</b>创建、修订和完成包含多个子任务的计划</li>
 *   <li><b>自动提示注入：</b>在每个推理步骤前注入上下文提示</li>
 *   <li><b>状态跟踪：</b>跟踪子任务状态（todo/in_progress/done/abandoned）</li>
 *   <li><b>历史计划：</b>存储和恢复历史计划</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // Create PlanNotebook with custom configuration
 * PlanNotebook planNotebook = PlanNotebook.builder()
 *     .planToHint(new DefaultPlanToHint())
 *     .storage(new InMemoryPlanStorage())
 *     .maxSubtasks(10)
 *     .build();
 *
 * // Create Agent with PlanNotebook (automatically registers tools and hook)
 * ReActAgent agent = ReActAgent.builder()
 *     .name("Assistant")
 *     .model(model)
 *     .toolkit(toolkit)
 *     .planNotebook(planNotebook)
 *     .build();
 *
 * // Or use default PlanNotebook configuration
 * ReActAgent agent = ReActAgent.builder()
 *     .name("Assistant")
 *     .model(model)
 *     .toolkit(toolkit)
 *     .enablePlan()
 *     .build();
 *
 * // Now agent will automatically receive hints before each reasoning step
 * agent.call(msg).block();
 * }</pre>
 *
 * <p><b>Tool Functions:</b> PlanNotebook provides 10 tool functions:
 *
 * <ul>
 *   <li>{@link #createPlan} - Create a new plan
 *   <li>{@link #updatePlanInfo} - Update current plan's name, description, or expected outcome
 *   <li>{@link #reviseCurrentPlan} - Add, revise, or delete subtasks
 *   <li>{@link #updateSubtaskState} - Update subtask state
 *   <li>{@link #finishSubtask} - Mark subtask as done
 *   <li>{@link #viewSubtasks} - View subtask details
 *   <li>{@link #getSubtaskCount} - Get the number of subtasks in current plan
 *   <li>{@link #finishPlan} - Finish or abandon plan
 *   <li>{@link #viewHistoricalPlans} - View historical plans
 *   <li>{@link #recoverHistoricalPlan} - Recover a historical plan
 * </ul>
 */
public class PlanNotebook implements StateModule {

    public static final String DESCRIPTION =
            "The plan-related tools. Activate this tool when you need to execute "
                    + "complex task, e.g. building a website or a game. Once activated, "
                    + "you'll enter the plan mode, where you will be guided to complete "
                    + "the given query by creating and following a plan, and hint message "
                    + "wrapped by <system-hint></system-hint> will guide you to complete "
                    + "the task. If you think the user no longer wants to perform the "
                    + "current task, you need to confirm with the user and call the "
                    + "'finish_plan' function.";

    /** 当前活动计划，为 null 表示无活跃计划。 */
    private Plan currentPlan;

    /** 计划转提示策略，负责将当前计划状态转换为 Agent 可理解的提示信息。 */
    private final PlanToHint planToHint;

    /** 计划持久化存储后端，用于保存和检索历史计划。 */
    private final PlanStorage storage;

    /** 每个计划允许的最大子任务数，为 null 表示不限制。 */
    private final Integer maxSubtasks;

    /** 执行前是否需要用户确认（默认为 true，Agent 需等待用户确认后才能执行计划）。 */
    private final boolean needUserConfirm;

    /** 计划变更回调映射表，当计划发生增删改时通知注册的监听器。 */
    private final Map<String, BiConsumer<PlanNotebook, Plan>> changeHooks;

    /** 存储键前缀，允许多个 PlanNotebook 实例在同一会话中共存。 */
    private String keyPrefix = "planNotebook";

    private PlanNotebook(Builder builder) {
        this.planToHint = builder.planToHint;
        this.storage = builder.storage;
        this.maxSubtasks = builder.maxSubtasks;
        this.needUserConfirm = builder.needUserConfirm;
        this.changeHooks = new ConcurrentHashMap<>();
        if (builder.keyPrefix != null) {
            this.keyPrefix = builder.keyPrefix;
        }
    }

    /**
     * Creates a new builder for constructing PlanNotebook instances.
     *
     * @return A new builder instance with default settings
     */
    public static Builder builder() {
        return new Builder();
    }

    // ==================== StateModule Implementation ====================

    /**
     * Save PlanNotebook state to the session.
     *
     * <p>Always saves the current state, including when currentPlan is null, to ensure cleared
     * state is persisted.
     *
     * @param session the session to save state to
     * @param sessionKey the session identifier
     */
    @Override
    public void saveTo(Session session, SessionKey sessionKey) {
        // Always save, even when null, to ensure cleared state is persisted
        session.save(sessionKey, keyPrefix + "_state", new PlanNotebookState(currentPlan));
    }

    /**
     * Load PlanNotebook state from the session.
     *
     * @param session the session to load state from
     * @param sessionKey the session identifier
     */
    @Override
    public void loadFrom(Session session, SessionKey sessionKey) {
        // Clear existing state first to avoid stale data
        this.currentPlan = null;
        session.get(sessionKey, keyPrefix + "_state", PlanNotebookState.class)
                .ifPresent(state -> this.currentPlan = state.currentPlan());
    }

    /** PlanNotebook 构建器，支持自定义配置。 */
    public static class Builder {

        /** 计划转提示策略，默认使用 {@link DefaultPlanToHint}。 */
        private PlanToHint planToHint = new DefaultPlanToHint();

        /** 计划持久化存储后端，默认使用内存存储 {@link InMemoryPlanStorage}。 */
        private PlanStorage storage = new InMemoryPlanStorage();

        /** 最大子任务数，默认不限制。 */
        private Integer maxSubtasks = null;

        /** 是否需要用户确认后才能执行计划，默认需要。 */
        private boolean needUserConfirm = true;

        /** 存储键前缀，默认使用类级默认值 "planNotebook"。 */
        private String keyPrefix = null;

        /**
         * 设置计划转提示的策略实现。
         *
         * @param planToHint 计划转提示转换器实现
         * @return 当前构建器实例（链式调用）
         */
        public Builder planToHint(PlanToHint planToHint) {
            this.planToHint = planToHint;
            return this;
        }

        /**
         * 设置历史计划的持久化存储后端。
         *
         * @param storage 计划存储实现
         * @return 当前构建器实例（链式调用）
         */
        public Builder storage(PlanStorage storage) {
            this.storage = storage;
            return this;
        }

        /**
         * 设置每个计划允许的最大子任务数。
         *
         * @param maxSubtasks 最大子任务数（null 表示不限制）
         * @return 当前构建器实例（链式调用）
         */
        public Builder maxSubtasks(int maxSubtasks) {
            this.maxSubtasks = maxSubtasks;
            return this;
        }

        /**
         * 设置是否需要用户确认后才能执行计划。
         *
         * <p>启用时（默认），提示中会包含一条规则，要求 Agent 在执行计划前等待用户明确确认。
         * 禁用时，Agent 创建计划后可立即执行。
         *
         * @param needUserConfirm true 需要用户确认，false 允许立即执行
         * @return 当前构建器实例（链式调用）
         */
        public Builder needUserConfirm(boolean needUserConfirm) {
            this.needUserConfirm = needUserConfirm;
            return this;
        }

        /**
         * 设置状态存储的键前缀。
         *
         * <p>当多个 PlanNotebook 实例需要在同一会话中共存时使用此配置。
         *
         * @param keyPrefix 存储键前缀（如 "mainPlan"、"subPlan"）
         * @return 当前构建器实例（链式调用）
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        /**
         * 使用已配置的设置构建 PlanNotebook 实例。
         *
         * @return 新的 PlanNotebook 实例
         */
        public PlanNotebook build() {
            return new PlanNotebook(this);
        }
    }

    // ==================== Tool Functions ====================

    /**
     * 根据给定的名称和子任务列表创建一个新计划。
     *
     * <p>如果当前已有活跃计划，新计划将替换旧计划。</p>
     *
     * @param name 计划名称，应简洁描述且不超过 10 个词
     * @param description 计划描述，包含约束条件、目标和预期成果，应清晰具体且可衡量
     * @param expectedOutcome 计划的预期成果，应具体、明确、可衡量
     * @param subtasks 构成计划的顺序子任务列表。每个子任务包含：name（必填）、description、expected_outcome
     * @return 工具响应消息
     */
    @Tool(name = "create_plan", description = "Create a plan by given name and sub-tasks")
    public Mono<String> createPlan(
            @ToolParam(
                            name = "name",
                            description =
                                    "The plan name, should be concise, descriptive and not exceed"
                                            + " 10 words")
                    String name,
            @ToolParam(
                            name = "description",
                            description =
                                    "The plan description, including the constraints, target and"
                                            + " outcome to be achieved. The description should be"
                                            + " clear, specific and concise, and all the"
                                            + " constraints, target and outcome should be specific"
                                            + " and measurable")
                    String description,
            @ToolParam(
                            name = "expected_outcome",
                            description =
                                    "The expected outcome of the plan, which should be specific,"
                                            + " concrete and measurable")
                    String expectedOutcome,
            @ToolParam(
                            name = "subtasks",
                            description =
                                    "A list of sequential sub-tasks. Each subtask must be an object"
                                            + " with: 'name' (string, required), 'description'"
                                            + " (string), 'expected_outcome' (string). Example:"
                                            + " [{\"name\": \"Calculate area\", \"description\":"
                                            + " \"Multiply length by width\", \"expected_outcome\":"
                                            + " \"Area value\"}]")
                    List<Map<String, Object>> subtasks) {

        // 将 Map 对象转换为 SubTask 领域对象
        List<SubTask> subtaskList = new ArrayList<>();
        for (Map<String, Object> subtaskMap : subtasks) {
            subtaskList.add(mapToSubTask(subtaskMap));
        }
        // 在创建计划前校验子任务数是否超过 maxSubtasks 上限
        // 提前拦截，避免创建超限计划
        if (maxSubtasks != null && subtaskList.size() > maxSubtasks) {
            return Mono.just(
                    String.format(
                            "Cannot create plan: the number of subtasks (%d) exceeds the maximum"
                                    + " limit of %d. Please reduce the number of subtasks.",
                            subtaskList.size(), maxSubtasks));
        }
        Plan plan = new Plan(name, description, expectedOutcome, subtaskList);

        // 如果已有活跃计划，新计划会替换旧计划
        String message;
        if (currentPlan == null) {
            message = String.format("Plan '%s' created successfully.", name);
        } else {
            message =
                    String.format(
                            "The current plan named '%s' is replaced by the newly created plan"
                                    + " named '%s'.",
                            currentPlan.getName(), name);
        }

        currentPlan = plan;
        return triggerPlanChangeHooks().thenReturn(message);
    }

    /**
     * 更新当前计划的名称、描述或预期成果。
     *
     * <p>每个参数都是可选的，传入 null 或空字符串表示不修改该字段。</p>
     *
     * @param name 新的计划名称（可选，传 null 或空字符串表示不修改）
     * @param description 新的计划描述（可选，传 null 或空字符串表示不修改）
     * @param expectedOutcome 新的预期成果（可选，传 null 或空字符串表示不修改）
     * @return 工具响应消息
     */
    @Tool(
            name = "update_plan_info",
            description =
                    "Update the current plan's name, description, or expected outcome. Pass null or"
                            + " empty string to keep a field unchanged.")
    public Mono<String> updatePlanInfo(
            @ToolParam(
                            name = "name",
                            description =
                                    "The new plan name (optional, pass null or empty to keep"
                                            + " unchanged)")
                    String name,
            @ToolParam(
                            name = "description",
                            description =
                                    "The new plan description (optional, pass null or empty to keep"
                                            + " unchanged)")
                    String description,
            @ToolParam(
                            name = "expected_outcome",
                            description =
                                    "The new expected outcome (optional, pass null or empty to keep"
                                            + " unchanged)")
                    String expectedOutcome) {

        validateCurrentPlan();

        StringBuilder changes = new StringBuilder();

        if (name != null && !name.trim().isEmpty()) {
            String oldName = currentPlan.getName();
            currentPlan.setName(name.trim());
            changes.append(String.format("name: '%s' -> '%s'", oldName, name.trim()));
        }

        if (description != null && !description.trim().isEmpty()) {
            currentPlan.setDescription(description.trim());
            if (!changes.isEmpty()) {
                changes.append(", ");
            }
            changes.append("description updated");
        }

        if (expectedOutcome != null && !expectedOutcome.trim().isEmpty()) {
            currentPlan.setExpectedOutcome(expectedOutcome.trim());
            if (!changes.isEmpty()) {
                changes.append(", ");
            }
            changes.append("expected_outcome updated");
        }

        if (changes.isEmpty()) {
            return Mono.just("No changes were made. Please provide at least one field to update.");
        }

        return triggerPlanChangeHooks()
                .thenReturn(
                        String.format(
                                "Plan '%s' updated successfully: %s.",
                                currentPlan.getName(), changes));
    }

    /**
     * Create a plan with SubTask objects (convenience method for tests and Java code).
     *
     * @param name The plan name
     * @param description The plan description
     * @param expectedOutcome The expected outcome
     * @param subtasks The list of SubTask objects
     * @return Tool response message
     */
    public Mono<String> createPlanWithSubTasks(
            String name, String description, String expectedOutcome, List<SubTask> subtasks) {
        return createPlan(name, description, expectedOutcome, subtasksToMaps(subtasks));
    }

    /**
     * Helper method to convert a list of SubTask objects to a list of Maps.
     *
     * @param subtasks List of SubTask objects
     * @return List of Maps
     */
    public static List<Map<String, Object>> subtasksToMaps(List<SubTask> subtasks) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (SubTask subtask : subtasks) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", subtask.getName() != null ? subtask.getName() : "Unnamed Subtask");
            map.put(
                    "description",
                    subtask.getDescription() != null ? subtask.getDescription() : "");
            map.put(
                    "expected_outcome",
                    subtask.getExpectedOutcome() != null ? subtask.getExpectedOutcome() : "");
            maps.add(map);
        }
        return maps;
    }

    /**
     * Helper method to convert a SubTask object to a Map.
     *
     * @param subtask SubTask object
     * @return Map representation
     */
    public static Map<String, Object> subtaskToMap(SubTask subtask) {
        if (subtask == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", subtask.getName() != null ? subtask.getName() : "Unnamed Subtask");
        map.put("description", subtask.getDescription() != null ? subtask.getDescription() : "");
        map.put(
                "expected_outcome",
                subtask.getExpectedOutcome() != null ? subtask.getExpectedOutcome() : "");
        return map;
    }

    /**
     * 修订当前计划：添加、修改或删除一个子任务。
     *
     * <p>支持三种操作：</p>
     * <ul>
     *   <li><b>add</b> — 在指定索引处插入新的子任务</li>
     *   <li><b>revise</b> — 替换指定索引处的子任务</li>
     *   <li><b>delete</b> — 删除指定索引处的子任务</li>
     * </ul>
     *
     * @param subtaskIdx 要操作的子任务索引，从 0 开始
     * @param action 操作类型：add（添加）/ revise（修改）/ delete（删除）
     * @param subtaskMap 子任务数据（add 和 revise 操作必填，delete 操作忽略）
     * @return 工具响应消息
     */
    @Tool(
            name = "revise_current_plan",
            description = "Revise the current plan by adding, revising or deleting a sub-task")
    public Mono<String> reviseCurrentPlan(
            @ToolParam(
                            name = "subtask_idx",
                            description =
                                    "The index of the sub-task to be revised, starting from 0")
                    int subtaskIdx,
            @ToolParam(
                            name = "action",
                            description = "The action to be performed: add/revise/delete")
                    String action,
            @ToolParam(
                            name = "subtask",
                            description =
                                    "The sub-task to be added or revised (required for add/revise)")
                    Map<String, Object> subtaskMap) {

        validateCurrentPlan();

        // 将 Map 转换为 SubTask 对象（如果提供了子任务数据）
        SubTask subtask = null;
        if (subtaskMap != null && !subtaskMap.isEmpty()) {
            subtask = mapToSubTask(subtaskMap);
        }

        // 校验操作类型是否合法
        if (!List.of("add", "revise", "delete").contains(action)) {
            return Mono.just(
                    String.format(
                            "Invalid action '%s'. Must be one of 'add', 'revise', 'delete'.",
                            action));
        }

        List<SubTask> subtasks = currentPlan.getSubtasks();

        // 校验索引范围：add 允许 0 到 size，其余操作允许 0 到 size-1
        if ("add".equals(action)) {
            if (subtaskIdx < 0 || subtaskIdx > subtasks.size()) {
                return Mono.just(
                        String.format(
                                "Invalid subtask_idx '%d' for action 'add'. Must be between 0 and"
                                        + " %d.",
                                subtaskIdx, subtasks.size()));
            }
            // 添加前检查子任务数上限（>= 表示已达上限，不能再添加）
            if (maxSubtasks != null && subtasks.size() >= maxSubtasks) {
                return Mono.just(
                        String.format(
                                "Cannot add more subtasks: the current plan has reached the"
                                        + " maximum limit of %d subtasks. Please delete some"
                                        + " existing subtasks first.",
                                maxSubtasks));
            }
        } else {
            if (subtaskIdx < 0 || subtaskIdx >= subtasks.size()) {
                return Mono.just(
                        String.format(
                                "Invalid subtask_idx '%d' for action '%s'. Must be between 0 and"
                                        + " %d.",
                                subtaskIdx, action, subtasks.size() - 1));
            }
        }

        // 根据操作类型执行对应的增/删/改
        return switch (action) {
            case "delete" -> {
                SubTask removed = subtasks.remove(subtaskIdx);
                yield triggerPlanChangeHooks()
                        .thenReturn(
                                String.format(
                                        "Subtask (named '%s') at index %d is deleted successfully.",
                                        removed.getName(), subtaskIdx));
            }
            case "add" -> {
                if (subtask == null) {
                    yield Mono.just("The subtask must be provided when action is 'add'.");
                }
                subtasks.add(subtaskIdx, subtask);
                yield triggerPlanChangeHooks()
                        .thenReturn(
                                String.format(
                                        "New subtask is added successfully at index %d.",
                                        subtaskIdx));
            }
            case "revise" -> {
                if (subtask == null) {
                    yield Mono.just("The subtask must be provided when action is 'revise'.");
                }
                subtasks.set(subtaskIdx, subtask);
                yield triggerPlanChangeHooks()
                        .thenReturn(
                                String.format(
                                        "Subtask at index %d is revised successfully.",
                                        subtaskIdx));
            }
            default -> Mono.just("Invalid action.");
        };
    }

    /**
     * 更新指定索引子任务的状态。
     *
     * <p>注意：标记子任务为"已完成"时，应使用 {@link #finishSubtask} 并提供具体成果，而非此方法。</p>
     *
     * @param subtaskIdx 要更新的子任务索引，从 0 开始
     * @param stateStr 新状态：todo（待处理）/ in_progress（进行中）/ abandoned（已放弃）
     * @return 工具响应消息
     */
    @Tool(
            name = "update_subtask_state",
            description = "Update the state of a subtask by given index and state")
    public Mono<String> updateSubtaskState(
            @ToolParam(
                            name = "subtask_idx",
                            description = "The index of the subtask to be updated, starting from 0")
                    int subtaskIdx,
            @ToolParam(name = "state", description = "The new state: todo/in_progress/abandoned")
                    String stateStr) {

        validateCurrentPlan();

        List<SubTask> subtasks = currentPlan.getSubtasks();

        // 校验子任务索引是否合法
        if (subtaskIdx < 0 || subtaskIdx >= subtasks.size()) {
            return Mono.just(
                    String.format(
                            "Invalid subtask_idx '%d'. Must be between 0 and %d.",
                            subtaskIdx, subtasks.size() - 1));
        }

        // 校验状态值是否合法，且不允许直接设置为 DONE
        SubTaskState state;
        try {
            state = SubTaskState.valueOf(stateStr.toUpperCase());
            if (state == SubTaskState.DONE) {
                return Mono.just(
                        "To mark a subtask as done, you SHOULD call 'finish_subtask' "
                                + "instead with the specific outcome.");
            }
        } catch (IllegalArgumentException e) {
            return Mono.just(
                    String.format(
                            "Invalid state '%s'. Must be one of 'todo', 'in_progress',"
                                    + " 'abandoned'.",
                            stateStr));
        }

        // 当设置为 IN_PROGRESS 时，校验状态转换规则
        if (state == SubTaskState.IN_PROGRESS) {
            // 检查所有前置子任务是否已完成或已放弃
            for (int i = 0; i < subtaskIdx; i++) {
                SubTask st = subtasks.get(i);
                if (st.getState() != SubTaskState.DONE && st.getState() != SubTaskState.ABANDONED) {
                    return Mono.just(
                            String.format(
                                    "Subtask (at index %d) named '%s' is not done yet. "
                                            + "You should finish the previous subtasks first.",
                                    i, st.getName()));
                }
            }

            // 检查是否已有其他子任务处于 IN_PROGRESS 状态
            for (int i = 0; i < subtasks.size(); i++) {
                SubTask st = subtasks.get(i);
                if (st.getState() == SubTaskState.IN_PROGRESS) {
                    return Mono.just(
                            String.format(
                                    "Subtask (at index %d) named '%s' is already 'in_progress'. "
                                            + "You should finish it first before starting another"
                                            + " subtask.",
                                    i, st.getName()));
                }
            }
        }

        subtasks.get(subtaskIdx).setState(state);
        return triggerPlanChangeHooks()
                .thenReturn(
                        String.format(
                                "Subtask at index %d, named '%s' is marked as '%s' successfully.",
                                subtaskIdx, subtasks.get(subtaskIdx).getName(), stateStr));
    }

    /**
     * 标记指定索引的子任务为已完成，并记录具体成果。
     *
     * <p>完成子任务后，如果存在下一个子任务，会自动将其激活（状态设为 IN_PROGRESS）。</p>
     *
     * @param subtaskIdx 要标记为完成的子任务索引，从 0 开始
     * @param outcome 子任务的具体成果，应与子任务描述中的预期成果一致。应为具体的数据、信息或文件路径，而非笼统的描述
     * @return 工具响应消息
     */
    @Tool(
            name = "finish_subtask",
            description = "Label the subtask as done by given index and outcome")
    public Mono<String> finishSubtask(
            @ToolParam(
                            name = "subtask_idx",
                            description =
                                    "The index of the sub-task to be marked as done, starting from"
                                            + " 0")
                    int subtaskIdx,
            @ToolParam(
                            name = "subtask_outcome",
                            description =
                                    "The specific outcome of the sub-task, should exactly match the"
                                        + " expected outcome in the sub-task description. SHOULDN'T"
                                        + " be what you did or general description, e.g. \"I have"
                                        + " searched xxx\", \"I have written the code for xxx\","
                                        + " etc. It SHOULD be the specific data, information, or"
                                        + " path to the file, e.g. \"There are 5 articles about"
                                        + " xxx, they are\\n"
                                        + "- xxx\\n"
                                        + "- xxx\\n"
                                        + "...\"")
                    String outcome) {

        validateCurrentPlan();

        List<SubTask> subtasks = currentPlan.getSubtasks();

        // 校验子任务索引是否合法
        if (subtaskIdx < 0 || subtaskIdx >= subtasks.size()) {
            return Mono.just(
                    String.format(
                            "Invalid subtask_idx '%d'. Must be between 0 and %d.",
                            subtaskIdx, subtasks.size() - 1));
        }

        // 检查所有前置子任务是否已完成或已放弃
        for (int i = 0; i < subtaskIdx; i++) {
            SubTask st = subtasks.get(i);
            if (st.getState() != SubTaskState.DONE && st.getState() != SubTaskState.ABANDONED) {
                return Mono.just(
                        String.format(
                                "Cannot finish subtask at index %d because the previous subtask "
                                        + "(at index %d) named '%s' is not done yet. "
                                        + "You should finish the previous subtasks first.",
                                subtaskIdx, i, st.getName()));
            }
        }

        // 完成当前子任务并记录成果
        subtasks.get(subtaskIdx).finish(outcome);

        // 如果存在下一个子任务，自动激活它
        String message;
        if (subtaskIdx + 1 < subtasks.size()) {
            SubTask nextSubtask = subtasks.get(subtaskIdx + 1);
            nextSubtask.setState(SubTaskState.IN_PROGRESS);
            message =
                    String.format(
                            "Subtask (at index %d) named '%s' is marked as done successfully. "
                                    + "The next subtask (at index %d) named '%s' is activated.",
                            subtaskIdx,
                            subtasks.get(subtaskIdx).getName(),
                            subtaskIdx + 1,
                            nextSubtask.getName());
        } else {
            message =
                    String.format(
                            "Subtask (at index %d) named '%s' is marked as done successfully.",
                            subtaskIdx, subtasks.get(subtaskIdx).getName());
        }

        return triggerPlanChangeHooks().thenReturn(message);
    }

    /**
     * 查看指定索引子任务的详细信息。
     *
     * @param indexes 要查看的子任务索引列表，从 0 开始
     * @return 包含子任务详细信息的工具响应消息
     */
    @Tool(
            name = "view_subtasks",
            description = "View the details of the sub-tasks by given indexes")
    public Mono<String> viewSubtasks(
            @ToolParam(
                            name = "subtask_idx",
                            description =
                                    "The indexes of the sub-tasks to be viewed, starting from 0")
                    List<Integer> indexes) {

        validateCurrentPlan();

        StringBuilder sb = new StringBuilder();
        List<SubTask> subtasks = currentPlan.getSubtasks();

        for (int idx : indexes) {
            if (idx >= 0 && idx < subtasks.size()) {
                sb.append(
                        String.format(
                                "Subtask at index %d:\n```\n%s\n```\n\n",
                                idx, subtasks.get(idx).toMarkdown(true)));
            } else {
                sb.append(
                        String.format(
                                "Invalid subtask_idx '%d'. Must be between 0 and %d.\n",
                                idx, subtasks.size() - 1));
            }
        }

        return Mono.just(sb.toString());
    }

    /**
     * 获取当前计划的子任务数量和统计信息。
     *
     * @return 包含子任务统计信息的工具响应消息（总数/已完成/进行中/待处理/已放弃）
     */
    @Tool(
            name = "get_subtask_count",
            description = "Get the number of subtasks in the current plan")
    public Mono<String> getSubtaskCount() {
        if (currentPlan == null) {
            return Mono.just("There is no active plan. Please create a plan first.");
        }

        List<SubTask> subtasks = currentPlan.getSubtasks();
        if (subtasks == null || subtasks.isEmpty()) {
            return Mono.just(
                    String.format("Current plan '%s' has 0 subtask(s).", currentPlan.getName()));
        }

        // 统计各状态的子任务数量
        int total = subtasks.size();
        int done = 0;
        int inProgress = 0;
        int todo = 0;
        int abandoned = 0;

        for (SubTask subtask : subtasks) {
            switch (subtask.getState()) {
                case DONE -> done++;
                case IN_PROGRESS -> inProgress++;
                case TODO -> todo++;
                case ABANDONED -> abandoned++;
            }
        }

        return Mono.just(
                String.format(
                        "Current plan '%s' has %d subtask(s): %d done, %d in_progress, %d todo, %d"
                                + " abandoned.",
                        currentPlan.getName(), total, done, inProgress, todo, abandoned));
    }

    /**
     * 完成或放弃当前计划。
     *
     * <p>完成后，计划会被保存到历史记录中，并从当前活跃状态中清除。</p>
     *
     * @param stateStr 计划结束状态：done（已完成）/ abandoned（已放弃）
     * @param outcome 完成时的具体成果，或放弃时的原因说明
     * @return 工具响应消息
     */
    @Tool(
            name = "finish_plan",
            description = "Finish the current plan by given outcome, or abandon it")
    public Mono<String> finishPlan(
            @ToolParam(name = "state", description = "The state to finish the plan: done/abandoned")
                    String stateStr,
            @ToolParam(
                            name = "outcome",
                            description =
                                    "The specific outcome of the plan if done, or reason if"
                                            + " abandoned")
                    String outcome) {

        if (currentPlan == null) {
            return Mono.just("There is no plan to finish.");
        }

        // 校验计划结束状态：仅允许 done 或 abandoned
        PlanState state;
        try {
            state = PlanState.valueOf(stateStr.toUpperCase());
            if (state != PlanState.DONE && state != PlanState.ABANDONED) {
                return Mono.just(
                        String.format(
                                "Invalid state '%s'. Must be 'done' or 'abandoned'.", stateStr));
            }
        } catch (IllegalArgumentException e) {
            return Mono.just(
                    String.format("Invalid state '%s'. Must be 'done' or 'abandoned'.", stateStr));
        }

        currentPlan.finish(state, outcome);

        String message =
                String.format("The current plan is finished successfully as '%s'.", stateStr);

        // 保存到历史记录 → 触发变更回调 → 清除当前计划
        return storage.addPlan(currentPlan)
                .then(triggerPlanChangeHooks())
                .then(Mono.fromRunnable(() -> currentPlan = null))
                .thenReturn(message);
    }

    /**
     * 查看所有已完成的计划历史记录。
     *
     * @return 包含历史计划摘要的工具响应消息
     */
    @Tool(name = "view_historical_plans", description = "View the historical plans")
    public Mono<String> viewHistoricalPlans() {
        return storage.getPlans()
                .map(
                        plans -> {
                            if (plans.isEmpty()) {
                                return "No historical plans found.";
                            }

                            StringBuilder sb = new StringBuilder();
                            for (Plan plan : plans) {
                                sb.append(
                                        String.format(
                                                "Plan named '%s':\n- ID: %s\n- Created at: %s\n"
                                                        + "- Description: %s\n- State: %s\n\n",
                                                plan.getName(),
                                                plan.getId(),
                                                plan.getCreatedAt(),
                                                plan.getDescription(),
                                                plan.getState().getValue()));
                            }
                            return sb.toString();
                        });
    }

    /**
     * 根据计划 ID 恢复一个历史计划，将其设为当前活跃计划。
     *
     * <p>如果当前已有活跃计划且未完成，会自动将其标记为"已放弃"并保存到历史记录中。</p>
     *
     * @param planId 要恢复的历史计划 ID
     * @return 工具响应消息
     */
    @Tool(
            name = "recover_historical_plan",
            description = "Recover a historical plan by given plan ID")
    public Mono<String> recoverHistoricalPlan(
            @ToolParam(
                            name = "plan_id",
                            description = "The ID of the historical plan to be recovered")
                    String planId) {

        return storage.getPlan(planId)
                .flatMap(
                        historicalPlan -> {
                            if (historicalPlan == null) {
                                return Mono.just(
                                        String.format(
                                                "Cannot find the plan with ID '%s'.", planId));
                            }

                            // 如果当前有活跃计划且未完成，先将其保存为已放弃
                            Mono<Void> saveCurrent = Mono.empty();
                            if (currentPlan != null) {
                                if (currentPlan.getState() != PlanState.DONE) {
                                    currentPlan.finish(
                                            PlanState.ABANDONED,
                                            String.format(
                                                    "The plan execution is interrupted by a new"
                                                            + " plan with ID '%s'.",
                                                    historicalPlan.getId()));
                                }
                                saveCurrent = storage.addPlan(currentPlan);
                            }

                            return saveCurrent.then(
                                    Mono.defer(
                                            () -> {
                                                String message;
                                                if (currentPlan != null) {
                                                    message =
                                                            String.format(
                                                                    "The current plan named '%s' is"
                                                                        + " replaced by the"
                                                                        + " historical plan named"
                                                                        + " '%s' with ID '%s'.",
                                                                    currentPlan.getName(),
                                                                    historicalPlan.getName(),
                                                                    historicalPlan.getId());
                                                } else {
                                                    message =
                                                            String.format(
                                                                    "Historical plan named '%s'"
                                                                            + " with ID '%s' is"
                                                                            + " recovered"
                                                                            + " successfully.",
                                                                    historicalPlan.getName(),
                                                                    historicalPlan.getId());
                                                }

                                                currentPlan = historicalPlan;
                                                return triggerPlanChangeHooks().thenReturn(message);
                                            }));
                        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据当前计划状态生成提示消息。
     *
     * <p>该方法由注入的 Hook 在每个推理步骤前自动调用，为 Agent 提供上下文引导。
     * 提示内容通过 {@link PlanToHint#generateHint} 策略生成。</p>
     *
     * @return 包含提示内容的 USER 角色消息的 Mono；如果无适用提示则返回空的 Mono
     */
    public Mono<Msg> getCurrentHint() {
        String hintContent = planToHint.generateHint(currentPlan, this);
        if (hintContent != null && !hintContent.isEmpty()) {
            return Mono.just(
                    Msg.builder()
                            .role(MsgRole.USER)
                            .name("user")
                            .content(List.of(TextBlock.builder().text(hintContent).build()))
                            .build());
        }
        return Mono.empty();
    }

    /**
     * 获取当前活跃计划。
     *
     * @return 当前计划对象，无活跃计划时返回 null
     */
    public Plan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * 检查执行计划前是否需要用户确认。
     *
     * @return true 需要用户确认，false 可直接执行
     */
    public boolean isNeedUserConfirm() {
        return needUserConfirm;
    }

    /**
     * 获取每个计划允许的最大子任务数。
     *
     * @return 最大子任务数，null 表示不限制
     */
    public Integer getMaxSubtasks() {
        return maxSubtasks;
    }

    /**
     * 注册一个计划变更回调，当计划发生增删改时触发。
     *
     * <p>回调接收 PlanNotebook 实例和当前计划（计划被完成或清除时可能为 null）。</p>
     *
     * @param id 回调的唯一标识符（用于后续移除）
     * @param hook 计划变更时执行的回调
     */
    public void addChangeHook(String id, BiConsumer<PlanNotebook, Plan> hook) {
        changeHooks.put(id, hook);
    }

    /**
     * 移除一个已注册的计划变更回调。
     *
     * @param id 要移除的回调标识符
     */
    public void removeChangeHook(String id) {
        changeHooks.remove(id);
    }

    /** 异步触发所有已注册的计划变更回调。 */
    private Mono<Void> triggerPlanChangeHooks() {
        return Flux.fromIterable(changeHooks.values())
                .flatMap(hook -> Mono.fromRunnable(() -> hook.accept(this, currentPlan)))
                .then();
    }

    /**
     * 将 Map 形式的子任务数据转换为 SubTask 领域对象。
     *
     * <p>对缺失的值提供默认处理：名称为空时使用 "Unnamed Subtask"，描述和预期成果为空字符串。</p>
     *
     * @param subtaskMap 包含 "name"、"description" 和 "expected_outcome" 键的 Map
     * @return 经过字段校验的 SubTask 对象
     */
    private SubTask mapToSubTask(Map<String, Object> subtaskMap) {
        String subtaskName = (String) subtaskMap.get("name");
        String subtaskDesc = (String) subtaskMap.get("description");
        String subtaskOutcome = (String) subtaskMap.get("expected_outcome");

        // 校验并设置默认值
        if (subtaskName == null || subtaskName.trim().isEmpty()) {
            subtaskName = "Unnamed Subtask";
        }
        if (subtaskDesc == null) {
            subtaskDesc = "";
        }
        if (subtaskOutcome == null) {
            subtaskOutcome = "";
        }

        return new SubTask(subtaskName, subtaskDesc, subtaskOutcome);
    }

    /**
     * 校验当前是否存在活跃计划，不存在则抛出异常。
     *
     * @throws IllegalStateException 如果当前计划为 null
     */
    private void validateCurrentPlan() {
        if (currentPlan == null) {
            throw new IllegalStateException(
                    "The current plan is None, you need to create a plan by calling "
                            + "create_plan() first.");
        }
    }
}
