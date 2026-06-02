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
package io.agentscope.harness.agent.subagent.task;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 描述后台子代理任务应如何执行的判别联合。
 *
 * <p>{@link LocalTaskRunSpec} 在本地执行器上运行供应商。{@link RemoteTaskRunSpec}
 * 委托给 AgentScope 任务 HTTP API（参见 {@code agentscope-extensions-agent-protocol}）。
 */
public sealed interface TaskRunSpec {

    /** In-process execution via {@link Supplier}. */
    record LocalTaskRunSpec(Supplier<String> execution) implements TaskRunSpec {}

    /**
     * Remote HTTP task execution. The {@code taskId} is chosen by the client and used as the
     * remote task key end-to-end.
     */
    record RemoteTaskRunSpec(
            String baseUrl, Map<String, String> headers, String agentId, String input)
            implements TaskRunSpec {}
}
