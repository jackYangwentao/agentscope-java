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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * TaskRepository 的默认实现，使用线程池进行后台执行。
 */
public class DefaultTaskRepository implements TaskRepository {

    private final Map<String, BackgroundTask> backgroundTasks = new ConcurrentHashMap<>();

    private final ExecutorService executor;

    private final boolean ownsExecutor;

    /**
     * 使用默认的缓存线程池执行器创建仓库。
     */
    public DefaultTaskRepository() {
        this(
                Executors.newCachedThreadPool(
                        r -> {
                            Thread thread = new Thread(r);
                            thread.setDaemon(true);
                            thread.setName("background-task-" + thread.getId());
                            return thread;
                        }),
                true);
    }

    /**
     * 使用自定义的执行器服务创建仓库。
     */
    public DefaultTaskRepository(ExecutorService executor) {
        this(executor, false);
    }

    /**
     * 内部构造函数，用于指定执行器所有权。
     */
    public DefaultTaskRepository(ExecutorService executor, boolean ownsExecutor) {
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
    }

    @Override
    public BackgroundTask getTask(String taskId) {
        return this.backgroundTasks.get(taskId);
    }

    @Override
    public BackgroundTask putTask(String taskId, Supplier<String> taskExecution) {
        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(taskExecution, this.executor);
        BackgroundTask backgroundTask = new BackgroundTask(taskId, future);
        this.backgroundTasks.put(taskId, backgroundTask);
        return backgroundTask;
    }

    @Override
    public void removeTask(String taskId) {
        this.backgroundTasks.remove(taskId);
    }

    @Override
    public void clear() {
        this.backgroundTasks.clear();
    }

    /**
     * 从仓库中移除已完成的任务。
     */
    public void clearCompletedTasks() {
        this.backgroundTasks.entrySet().removeIf(entry -> entry.getValue().isCompleted());
    }

    /**
     * 如果此仓库拥有执行器则关闭它。
     */
    public void shutdown() {
        if (this.ownsExecutor && this.executor != null) {
            this.executor.shutdown();
            try {
                if (!this.executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    this.executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
