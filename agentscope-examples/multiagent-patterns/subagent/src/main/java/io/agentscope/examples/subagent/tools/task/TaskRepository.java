/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.examples.subagent.tools.task;

import java.util.function.Supplier;

/**
 * 用于管理后台任务的仓库。
 * <p>
 * 受 spring ai TaskRepository 启发，使主 agent 能够在后台启动子 agent
 * 并通过 TaskOutputTool 稍后检索结果。
 *
 */
public interface TaskRepository {

    /**
     * 根据 ID 获取后台任务。
     * @param taskId 任务标识符
     * @return 后台任务，如果未找到则返回 null
     */
    BackgroundTask getTask(String taskId);

    /**
     * 向仓库添加新的后台任务。
     * @param taskId 任务标识符
     * @param taskExecution 执行任务并返回输出的 Supplier
     * @return 创建的后台任务
     */
    BackgroundTask putTask(String taskId, Supplier<String> taskExecution);

    /**
     * 从仓库中移除后台任务。
     * @param taskId 任务标识符
     */
    void removeTask(String taskId);

    /**
     * 清除仓库中的所有任务。
     */
    void clear();
}
