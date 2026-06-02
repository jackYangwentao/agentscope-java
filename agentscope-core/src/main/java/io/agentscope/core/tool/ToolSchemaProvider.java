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

import io.agentscope.core.model.ToolSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 以多种格式提供工具 Schema 供模型使用。
 *
 * <p>该类负责生成发送给 LLM 的工具 Schema，告知其可用的工具。它根据激活的工具组进行过滤，
 * 确保只有来自激活组的工具（或未分组的工具）包含在 Schema 中。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>生成 OpenAI 格式的工具 Schema（基于 Map 的表示）</li>
 *   <li>生成 {@link ToolSchema} 对象格式的工具 Schema 供模型 API 使用</li>
 *   <li>根据 {@link ToolGroupManager} 激活状态过滤工具</li>
 *   <li>使用 {@link RegisteredToolFunction} 的扩展参数生成准确的 Schema</li>
 * </ul>
 *
 * <p><b>过滤逻辑：</b>工具被包含在 Schema 中当且仅当：
 * <ul>
 *   <li>该工具未分组（未分配给任何工具组），或者</li>
 *   <li>它属于至少一个当前激活的工具组</li>
 * </ul>
 */
class ToolSchemaProvider {

    private final ToolRegistry toolRegistry;
    private final ToolGroupManager groupManager;

    /**
     * Creates a ToolSchemaProvider with the given registry and group manager.
     *
     * @param toolRegistry The tool registry to retrieve registered tools from
     * @param groupManager The group manager to check tool group activation status
     */
    ToolSchemaProvider(ToolRegistry toolRegistry, ToolGroupManager groupManager) {
        this.toolRegistry = toolRegistry;
        this.groupManager = groupManager;
    }

    /**
     * Get tool schemas as ToolSchema objects for model consumption.
     * Updated to respect active tool groups.
     *
     * @return List of ToolSchema objects
     */
    List<ToolSchema> getToolSchemas() {
        List<ToolSchema> schemas = new ArrayList<>();
        List<RegisteredToolFunction> registeredTools =
                new ArrayList<>(toolRegistry.getAllRegisteredTools().values());
        Set<String> activeTools = groupManager.getActiveToolNames();

        for (RegisteredToolFunction registered : registeredTools) {
            AgentTool tool = registered.getTool();
            String toolName = tool.getName();

            // Filter: include ungrouped tools, or tools in any active group
            if (groupManager.isGroupedTool(toolName) && !activeTools.contains(toolName)) {
                continue; // Skip inactive grouped tools
            }

            ToolSchema schema =
                    ToolSchema.builder()
                            .name(toolName)
                            .description(tool.getDescription())
                            .parameters(registered.getExtendedParameters())
                            .strict(tool.getStrict())
                            .outputSchema(tool.getOutputSchema())
                            .build();
            schemas.add(schema);
        }

        return schemas;
    }
}
