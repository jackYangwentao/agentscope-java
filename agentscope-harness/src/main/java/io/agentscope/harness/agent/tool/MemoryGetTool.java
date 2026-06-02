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
package io.agentscope.harness.agent.tool;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Path;
import java.util.List;

/**
 * 用于从记忆文件中读取特定行的工具，通常在 {@link MemorySearchTool} 之后使用，
 * 以获取匹配行周围的上下文内容。
 * Tool for reading specific lines from memory files, typically used after
 * {@link MemorySearchTool} to fetch surrounding context.
 */
public class MemoryGetTool {

    private final WorkspaceManager workspaceManager;

    public MemoryGetTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    /**
     * 从记忆文件中读取指定行范围的内容。在 memory_search 之后使用，
     * 以获取匹配行周围的完整上下文。路径相对于工作空间。
     * Read specific lines from a memory file. Use after memory_search to pull
     * full context around matched lines. Path is relative to workspace.
     *
     * @Tool memory_get
     */
    @Tool(
            name = "memory_get",
            description =
                    "Read specific lines from a memory file. Use after memory_search to pull"
                            + " full context around matched lines. Path is relative to workspace.")
    public String memoryGet(
            RuntimeContext runtimeContext,
            @ToolParam(
                            name = "path",
                            description =
                                    "Relative path to the memory file (e.g., MEMORY.md or"
                                            + " memory/2026-04-01.md)")
                    String path,
            @ToolParam(name = "startLine", description = "Start line number (1-based, inclusive)")
                    int startLine,
            @ToolParam(name = "endLine", description = "End line number (1-based, inclusive)")
                    int endLine) {
        if (path == null || path.isBlank()) {
            return "Error: path is required";
        }

        // 解析并验证路径，防止路径遍历攻击
        Path resolved = workspaceManager.getWorkspace().resolve(path).normalize();
        if (!resolved.startsWith(workspaceManager.getWorkspace())) {
            return "Error: path traversal not allowed";
        }

        RuntimeContext rc = runtimeContext != null ? runtimeContext : RuntimeContext.empty();
        String text = workspaceManager.readManagedWorkspaceFileUtf8(rc, path);
        if (text == null || text.isBlank()) {
            return "Error: file not found: " + path;
        }

        List<String> lines = List.of(text.split("\n", -1));
        int start = Math.max(0, startLine - 1);
        int end = Math.min(lines.size(), endLine);

        if (start >= lines.size()) {
            return "Error: startLine " + startLine + " exceeds file length " + lines.size();
        }

        // 构建带行号的结果输出
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(String.format("%d|%s%n", i + 1, lines.get(i)));
        }
        return sb.toString();
    }
}
