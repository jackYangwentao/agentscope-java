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
package io.agentscope.harness.agent.subagent;

/**
 * 控制如何确定声明的子代理的运行时工作区根目录。
 *
 * <p>五行决策表：
 *
 * <pre>
 * workspacePath  mode      runtime-workspace-root
 * ─────────────────────────────────────────────────────────────────────────────
 * set            ISOLATED  workspacePath  (定义目录也是运行时根目录)
 * set            SHARED    mainWorkspace  (定义技能/知识被忽略)
 * null           ISOLATED  mainWorkspace/agents/&lt;name&gt;/workspace/  (自动创建)
 * null           SHARED    mainWorkspace
 * (通用目的，始终 SHARED)  mainWorkspace  (完全镜像主代理)
 * </pre>
 */
public enum WorkspaceMode {

    /**
     * The subagent gets its own isolated workspace.
     *
     * <ul>
     *   <li>If {@link SubagentDeclaration#getWorkspacePath()} is set, that path is the runtime
     *       root and also the source for the sysPrompt ({@code AGENTS.md}).
     *   <li>Otherwise the runtime root is auto-created at
     *       {@code mainWorkspace/agents/&lt;name&gt;/workspace/} and the inline body is used as
     *       sysPrompt.
     * </ul>
     */
    ISOLATED,

    /**
     * The subagent shares the main agent's workspace.
     *
     * <ul>
     *   <li>The runtime root is always {@code mainWorkspace}, regardless of
     *       {@link SubagentDeclaration#getWorkspacePath()}.
     *   <li>If {@code workspacePath} is set, its {@code AGENTS.md} is used as the sysPrompt body;
     *       but the definition's {@code skills/}, {@code knowledge/}, and {@code MEMORY.md} are
     *       ignored.
     *   <li>If {@code workspacePath} is absent, the inline body is used as sysPrompt.
     * </ul>
     */
    SHARED
}
