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
import java.lang.reflect.Type;

/**
 * 将工具方法返回值转换为 ToolResultBlock。
 * 自定义实现可以覆盖特定工具的转换逻辑。
 *
 * <p>该接口允许用户自定义工具结果如何转换并呈现给 LLM。实现可以控制 JSON 序列化、
 * 添加元数据、过滤敏感数据或压缩大量输出。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class CustomConverter implements ToolResultConverter {
 *     @Override
 *     public ToolResultBlock convert(Object result, Type returnType) {
 *         // Custom conversion logic
 *         return ToolResultBlock.of(...);
 *     }
 * }
 *
 * @Tool(
 *     name = "my_tool",
 *     converter = CustomConverter.class
 * )
 * public String myTool() { ... }
 * }</pre>
 *
 * @see DefaultToolResultConverter
 */
public interface ToolResultConverter {

    /**
     * Convert tool call result to ToolResultBlock.
     *
     * @param result the tool call result (may be null)
     * @param returnType the return type of the tool method (may be null)
     * @return ToolResultBlock containing the converted result (never null)
     */
    ToolResultBlock convert(Object result, Type returnType);
}
