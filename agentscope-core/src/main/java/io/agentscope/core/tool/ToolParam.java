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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于描述工具方法参数的注解。
 *
 * <p>对于所有被 {@link Tool} 注解的方法的参数（除自动注入的 {@link ToolEmitter} 外），此注解是必需的。
 * 它提供元数据用于生成描述工具参数的 JSON Schema，供 LLM 使用。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Tool(name = "calculate_area", description = "Calculate rectangle area")
 * public double calculateArea(
 *     @ToolParam(name = "width", description = "Width in meters", required = true)
 *     double width,
 *     @ToolParam(name = "height", description = "Height in meters", required = true)
 *     double height,
 *     @ToolParam(name = "unit", description = "Unit of measurement", required = false)
 *     String unit
 * ) {
 *     // Implementation
 * }
 * }</pre>
 *
 * <p><b>重要说明：</b>
 * <ul>
 *   <li>{@code name} 属性<b>必需</b>，因为 Java 默认不会在运行时保留参数名称</li>
 *   <li>参数名称应遵循 snake_case 命名约定以兼容 LLM</li>
 *   <li>描述帮助 LLM 理解应提供何种参数值</li>
 *   <li>{@link ToolEmitter} 参数无需此注解（由框架自动注入）</li>
 * </ul>
 *
 * @see Tool
 * @see ToolEmitter
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParam {

    /**
     * The name of the tool parameter.
     *
     * <p><b>This attribute is required</b> because Java does not preserve parameter names at
     * runtime by default (unless compiled with {@code -parameters} flag, which is not reliable).
     * The toolkit uses this name to map LLM-provided arguments to method parameters.
     *
     * <p>Names should follow snake_case convention (e.g., "file_path", "max_results") for
     * compatibility with various LLM providers.
     *
     * @return The parameter name as it should appear in the tool schema
     */
    String name();

    /**
     * Whether this parameter is required.
     *
     * <p>Required parameters must be provided by the LLM when invoking the tool. Optional
     * parameters can be omitted, and the method will receive null (for objects) or default values
     * (for primitives).
     *
     * @return true if required (default), false if optional
     */
    boolean required() default true;

    /**
     * The description of this parameter.
     *
     * <p>This description is sent to the LLM as part of the tool schema to help it understand:
     * <ul>
     *   <li>What this parameter represents</li>
     *   <li>What format or values are expected</li>
     *   <li>Any constraints or validation rules</li>
     * </ul>
     *
     * <p>Good descriptions improve the LLM's ability to provide correct parameter values.
     *
     * @return The parameter description, or empty string if not provided
     */
    String description() default "";
}
