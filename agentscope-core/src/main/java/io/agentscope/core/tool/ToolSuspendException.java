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

/**
 * 表示工具执行应被挂起并由外部处理的异常。
 *
 * <p>此异常由需要外部执行的工具使用（例如，通过仅注册 Schema 注册的工具）。抛出时，框架将：
 * <ol>
 *   <li>将异常转换为待处理的 {@code ToolResultBlock}</li>
 *   <li>使用 {@code GenerateReason.TOOL_SUSPENDED} 向用户返回挂起消息</li>
 *   <li>等待用户提供工具执行结果</li>
 * </ol>
 *
 * <p>在自定义工具中的使用示例：
 * <pre>{@code
 * @Tool(name = "external_api", description = "Call external API")
 * public ToolResultBlock callExternalApi(@ToolParam(name = "url") String url) {
 *     // Signal that this tool needs external execution
 *     throw new ToolSuspendException("Requires external API call to: " + url);
 * }
 * }</pre>
 *
 * @see SchemaOnlyTool
 */
public class ToolSuspendException extends RuntimeException {

    private final String reason;

    /**
     * Creates a new ToolSuspendException with default message.
     */
    public ToolSuspendException() {
        this(null);
    }

    /**
     * Creates a new ToolSuspendException with a custom reason.
     *
     * @param reason the reason for suspension, will be included in the pending ToolResultBlock
     */
    public ToolSuspendException(String reason) {
        super(reason != null ? reason : "Tool execution suspended");
        this.reason = reason;
    }

    /**
     * Gets the user-defined reason for suspension.
     *
     * @return the reason, or null if not specified
     */
    public String getReason() {
        return reason;
    }
}
