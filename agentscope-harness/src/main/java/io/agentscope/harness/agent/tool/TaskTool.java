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
package io.agentscope.harness.agent.tool;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.subagent.task.BackgroundTask;
import io.agentscope.harness.agent.subagent.task.TaskRepository;
import io.agentscope.harness.agent.subagent.task.TaskStatus;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * 后台任务生命周期管理的统一工具。将任务结果获取、取消和列表功能合并到一个工具类中。
 * Unified tool for background task lifecycle management. Combines task result retrieval,
 * cancellation, and listing into a single tool class.
 *
 * <ul>
 *   <li>{@code task_output} — 获取结果（阻塞或非阻塞）
 *   <li>{@code task_cancel} — 取消运行中的任务
 *   <li>{@code task_list} — 列出所有追踪的任务，可选择按状态过滤
 * </ul>
 *
 * <p>所有操作通过 {@link RuntimeContext} 限定在当前父会话 ID 范围内。
 * {@link TaskRepository} 在没有本地 future 时（跨节点或重启后场景）回退到工作空间持久化记录。
 */
public class TaskTool {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final TaskRepository taskRepository;

    public TaskTool(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 获取后台子代理任务的输出。当 agent_spawn 或 agent_send 以 timeout_seconds=0 调用时使用。
     * 优先使用 block=false 在不等待的情况下检查状态。仅在准备好等待结果时使用 block=true（默认值）。
     * 不要在启动任务后立即调用——对话历史中的任务状态可能已过时；
     * 始终使用 task_output 或 task_list 获取最新状态。
     *
     * @Tool task_output
     */
    @Tool(
            name = "task_output",
            description =
                    "Retrieve the output of a background subagent task. Use when agent_spawn or"
                        + " agent_send was called with timeout_seconds=0. Prefer block=false to"
                        + " check status without waiting. Only use block=true (the default) when"
                        + " you are ready to wait for the result. Do NOT call this immediately"
                        + " after launching a task — the task status in conversation history is"
                        + " stale; always call task_output or task_list to get the current state.")
    public String taskOutput(
            RuntimeContext runtimeContext,
            @ToolParam(
                            name = "task_id",
                            description =
                                    "The task_id returned by agent_spawn or agent_send when"
                                            + " timeout_seconds was 0")
                    String taskId,
            @ToolParam(
                            name = "block",
                            description =
                                    "Whether to wait for completion (default: true). Prefer"
                                            + " false for status checks to avoid blocking.",
                            required = false)
                    Boolean block,
            @ToolParam(
                            name = "timeout",
                            description =
                                    "Max wait time in milliseconds (default: 30000, max: 600000)",
                            required = false)
                    Long timeout) {

        if (taskId == null || taskId.isBlank()) {
            return "Error: task_id is required";
        }

        String sessionId = runtimeContext != null ? runtimeContext.getSessionId() : null;
        BackgroundTask bgTask = taskRepository.getTask(runtimeContext, sessionId, taskId);
        if (bgTask == null) {
            return "Error: No background task found with ID: "
                    + taskId
                    + ". Use task_list() to see all known tasks for this session.";
        }

        bgTask.updateLastCheckedAt();

        boolean shouldBlock = block == null || block;
        long timeoutMs = timeout != null ? Math.min(timeout, 600_000) : 30_000;

        if (shouldBlock && !bgTask.isCompleted()) {
            // 如果任务没有本地 future（跨节点或重启后），优雅降级而不是无限阻塞
            if (bgTask.getTaskStatus() == TaskStatus.PENDING
                    || bgTask.getTaskStatus() == TaskStatus.RUNNING) {
                try {
                    bgTask.waitForCompletion(timeoutMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Error: Wait for task interrupted";
                }
                // 等待后如果仍未完成，可能正在其他节点上运行
                if (!bgTask.isCompleted()) {
                    return "task_id: "
                            + taskId
                            + "\nstatus: running"
                            + "\nnote: Task is running (possibly on another node)."
                            + " Use task_output(block=false) to poll for completion.";
                }
            }
        }

        return formatTaskDetail(bgTask);
    }

    /**
     * 取消运行中的后台任务。用于停止不再需要的任务。对已完成的任务无影响。
     *
     * @Tool task_cancel
     */
    @Tool(
            name = "task_cancel",
            description =
                    "Cancel a running background task. Use to stop a task that is no longer"
                            + " needed. Has no effect on already-completed tasks.")
    public String taskCancel(
            RuntimeContext runtimeContext,
            @ToolParam(name = "task_id", description = "The task_id to cancel") String taskId) {

        if (taskId == null || taskId.isBlank()) {
            return "Error: task_id is required";
        }

        String sessionId = runtimeContext != null ? runtimeContext.getSessionId() : null;
        BackgroundTask bgTask = taskRepository.getTask(runtimeContext, sessionId, taskId);
        if (bgTask == null) {
            return "Error: No background task found with ID: " + taskId;
        }

        TaskStatus currentStatus = bgTask.getTaskStatus();
        if (currentStatus.isTerminal()) {
            return "task_id: "
                    + taskId
                    + "\nstatus: "
                    + currentStatus.name().toLowerCase()
                    + "\nnote: Task already in terminal state, cannot cancel.";
        }

        taskRepository.cancelTask(runtimeContext, sessionId, taskId);
        return "task_id: " + taskId + "\nstatus: cancelled\nCancellation requested successfully.";
    }

    /**
     * 列出当前会话的所有后台任务及其当前状态。
     * 从持久化工作空间存储读取——即使在会话压缩或节点迁移后也始终准确。
     * 可选择按状态过滤（running、completed、failed、cancelled）。
     * 使用此工具在压缩后恢复任务 ID 和状态。
     *
     * @Tool task_list
     */
    @Tool(
            name = "task_list",
            description =
                    "List all background tasks for the current session with their current statuses."
                        + " Reads from durable workspace storage — always accurate even after"
                        + " conversation compaction or node migration. Optionally filter by status"
                        + " (running, completed, failed, cancelled). Use this to recover task IDs"
                        + " and state after compaction.")
    public String taskList(
            RuntimeContext runtimeContext,
            @ToolParam(
                            name = "status_filter",
                            description =
                                    "Filter by status: running, completed, failed, cancelled, or"
                                            + " omit for all tasks",
                            required = false)
                    String statusFilter) {

        TaskStatus filter = parseStatusFilter(statusFilter);
        String sessionId = runtimeContext != null ? runtimeContext.getSessionId() : null;
        Collection<BackgroundTask> tasks =
                taskRepository.listTasks(runtimeContext, sessionId, filter);

        if (tasks.isEmpty()) {
            String filterDesc =
                    filter != null ? " with status '" + filter.name().toLowerCase() + "'" : "";
            return "No background tasks tracked" + filterDesc + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(tasks.size()).append(" tracked task(s):\n");
        for (BackgroundTask task : tasks) {
            sb.append("- task_id: ").append(task.getTaskId());
            if (task.getAgentId() != null) {
                sb.append("  agent: ").append(task.getAgentId());
            }
            sb.append("  status: ").append(task.getTaskStatus().name().toLowerCase());
            sb.append("  created: ").append(ISO_FORMATTER.format(task.getCreatedAt()));
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /** 解析可选的状态过滤器参数。 */
    private static TaskStatus parseStatusFilter(String filter) {
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter.trim())) {
            return null;
        }
        try {
            return TaskStatus.valueOf(filter.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 格式化后台任务的详细信息输出。 */
    private static String formatTaskDetail(BackgroundTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("task_id: ").append(task.getTaskId()).append('\n');
        if (task.getAgentId() != null) {
            sb.append("agent_id: ").append(task.getAgentId()).append('\n');
        }
        sb.append("status: ").append(task.getStatus()).append('\n');
        sb.append("created_at: ").append(ISO_FORMATTER.format(task.getCreatedAt())).append('\n');

        if (task.isCompleted() && task.getResult() != null) {
            sb.append("\nResult:\n").append(task.getResult());
        } else if (task.getError() != null) {
            Exception err = task.getError();
            sb.append("\nError:\n").append(err.getMessage());
            if (err.getCause() != null) {
                sb.append("\nCause: ").append(err.getCause().getMessage());
            }
        } else if (!task.isCompleted()) {
            sb.append("\nTask still running...");
        } else {
            sb.append("\nTask completed with no result.");
        }
        return sb.toString();
    }
}
