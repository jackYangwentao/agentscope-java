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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用 CompletableFuture 管理后台任务的执行。
 * <p>
 * 提供对任务状态、结果和错误信息的线程安全访问。
 * 任务在构造时自动启动。调用者可以检查完成状态、等待完成、取消任务以及获取结果或错误。
 *
 */
public class BackgroundTask {

    private final String taskId;

    private final CompletableFuture<String> future;

    /**
     * 使用已存在的 future 创建 BackgroundTask。
     * @param taskId 任务标识符
     * @param future 要包装的 CompletableFuture
     */
    public BackgroundTask(String taskId, CompletableFuture<String> future) {
        this.taskId = taskId;
        this.future = future;
    }

    /**
     * 检查任务是否已完成执行。
     */
    public boolean isCompleted() {
        return this.future.isDone();
    }

    /**
     * 获取任务执行结果（非阻塞）。
     * @return 任务结果，如果尚未完成或发生错误则返回 null
     */
    public String getResult() {
        try {
            return this.future.getNow(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取任务执行过程中发生的错误（如果有）。
     */
    public Exception getError() {
        if (this.future.isCompletedExceptionally()) {
            try {
                this.future.getNow(null);
            } catch (Exception e) {
                if (e.getCause() instanceof Exception cause) {
                    return cause;
                }
                return e;
            }
        }
        return null;
    }

    /**
     * 获取任务的人类可读状态描述。
     */
    public String getStatus() {
        if (this.future.isCompletedExceptionally()) {
            Exception error = getError();
            return "Failed: " + (error != null ? error.getMessage() : "Unknown error");
        }
        return this.future.isDone() ? "Completed" : "Running";
    }

    /**
     * 在指定超时时间内等待任务完成。
     * @param timeoutMs 最大等待时间（毫秒）
     * @return 如果任务在超时内完成则返回 true，超时返回 false
     */
    public boolean waitForCompletion(long timeoutMs) throws InterruptedException {
        if (this.future.isDone()) {
            return true;
        }
        try {
            this.future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            throw e;
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 如果任务尚未完成，则取消任务。
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return this.future.cancel(mayInterruptIfRunning);
    }

    /**
     * 获取任务 ID。
     */
    public String getTaskId() {
        return this.taskId;
    }
}
