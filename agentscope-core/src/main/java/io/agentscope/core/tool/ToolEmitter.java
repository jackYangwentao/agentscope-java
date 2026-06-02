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
package io.agentscope.core.tool;

import io.agentscope.core.message.ToolResultBlock;

/**
 * 在工具执行期间发送流式响应的接口。
 *
 * <p>工具方法可以声明 ToolEmitter 参数，在执行期间发送中间进度更新和消息。
 * 这些流式数据块通过 {@code onActingChunk()} 事件传递给已注册的钩子，但<b>不会</b>发送给 LLM。
 * 只有工具方法的最终返回值会作为工具结果发送给 LLM。
 *
 * <p><b>关键特性：</b>
 * <ul>
 *   <li>ToolEmitter 由框架自动注入——无需 {@link ToolParam} 注解</li>
 *   <li>发送的数据块传递到钩子（用于监控/日志），而非 LLM</li>
 *   <li>适用于长时间运行的工具以提供进度反馈</li>
 *   <li>不影响 LLM 可见的工具 Schema</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Tool(name = "long_task", description = "Execute a long running task")
 * public ToolResultBlock execute(
 *     @ToolParam(name = "input") String input,
 *     ToolEmitter emitter  // Automatically injected by framework
 * ) {
 *     emitter.emit(ToolResultBlock.text("Starting task..."));
 *
 *     // Step 1
 *     processStep1(input);
 *     emitter.emit(ToolResultBlock.text("Step 1 completed"));
 *
 *     // Step 2
 *     processStep2(input);
 *     emitter.emit(ToolResultBlock.text("Step 2 completed"));
 *
 *     // Final result (this is what the LLM sees)
 *     return ToolResultBlock.text("Task completed successfully");
 * }
 * }</pre>
 */
public interface ToolEmitter {

    /**
     * Emit a streaming response chunk during tool execution.
     *
     * <p>This method sends intermediate messages to registered hooks via {@code onToolChunk()}.
     * The emitted chunks do NOT affect what the LLM receives - only the tool method's return value
     * is sent to the LLM.
     *
     * @param chunk The chunk to emit
     */
    void emit(ToolResultBlock chunk);
}
