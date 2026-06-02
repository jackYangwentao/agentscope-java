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
 * 用于将方法标记为可被 AI 智能体调用的工具的注解。
 *
 * <p>被 {@code @Tool} 注解的方法会自动注册到工具集中，并可供智能体执行。工具集通过反射发现工具方法，
 * 并生成相应的 JSON Schema 供 LLM 使用。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * public class WeatherTools {
 *     @Tool(name = "get_weather", description = "Get current weather for a city")
 *     public String getWeather(
 *         @ToolParam(name = "city", description = "City name") String city,
 *         @ToolParam(name = "unit", description = "Temperature unit") String unit) {
 *         // Implementation
 *         return "Weather data...";
 *     }
 * }
 * }</pre>
 *
 * <p><b>要求：</b>
 * <ul>
 *   <li>所有参数必须使用 {@link ToolParam} 注解（{@link ToolEmitter} 除外）</li>
 *   <li>返回类型必须为 String、Mono&lt;String&gt; 或其他响应式类型</li>
 *   <li>工具名称应遵循 snake_case 命名约定以兼容 LLM</li>
 *   <li>描述应清晰说明工具的功能及使用场景</li>
 * </ul>
 *
 * @see ToolParam
 * @see Toolkit
 * @see ToolEmitter
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {

    /**
     * The name of the tool.
     *
     * <p>If not provided, the method name will be used. Tool names should follow snake_case
     * convention (e.g., "get_weather", "send_email") for compatibility with various LLM providers.
     *
     * @return The tool name, or empty string to use method name
     */
    String name() default "";

    /**
     * The description of the tool that explains its purpose and usage.
     *
     * <p>This description is sent to the LLM to help it decide when to invoke the tool. It should
     * clearly explain:
     * <ul>
     *   <li>What the tool does</li>
     *   <li>When it should be used</li>
     *   <li>What kind of results it returns</li>
     * </ul>
     *
     * <p>If not provided, a generic description based on the method name will be generated.
     *
     * @return The tool description, or empty string to auto-generate
     */
    String description() default "";

    /**
     * Whether to enable strict schema mode for this tool.
     *
     * <p>When enabled, compatible model providers can enforce stronger adherence to the declared
     * JSON schema for tool arguments.
     *
     * @return true to enable strict mode for this tool
     */
    boolean strict() default false;

    /**
     * Custom result converter for this tool.
     *
     * <p>Converters transform tool method return values into {@link io.agentscope.core.message.ToolResultBlock}
     * instances suitable for LLM consumption. Use custom converters to:
     * <ul>
     *   <li>Filter sensitive data from results</li>
     *   <li>Format output in specific ways</li>
     *   <li>Add metadata to results</li>
     *   <li>Compress or summarize large outputs</li>
     * </ul>
     *
     * <p><b>Usage Example:</b>
     * <pre>{@code
     * @Tool(
     *     name = "get_data",
     *     converter = CustomJsonConverter.class
     * )
     * public MyData getData(String id) {
     *     return dataService.findById(id);
     * }
     * }</pre>
     *
     * <p>If not specified, the default converter ({@link DefaultToolResultConverter}) is used,
     * which provides JSON serialization with schema information.
     *
     * <p><b>Note:</b> If you need complex processing with multiple steps, implement your own
     * converter that combines the necessary logic.
     *
     * @return Converter class
     * @see ToolResultConverter
     * @see DefaultToolResultConverter
     */
    Class<? extends ToolResultConverter> converter() default DefaultToolResultConverter.class;
}
