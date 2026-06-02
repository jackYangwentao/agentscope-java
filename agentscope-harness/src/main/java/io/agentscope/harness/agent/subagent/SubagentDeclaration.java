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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 声明一个子代理：其身份、工作区解析策略和可选的能力白名单。
 *
 * <p>声明绑定到两种<em>源模式</em>之一：
 *
 * <ol>
 *   <li><b>定义工作区</b> — {@link #getWorkspacePath()} 指向至少包含 {@code AGENTS.md} 的工作区目录。
 *       该文件用作子代理的系统提示词正文。当 {@link WorkspaceMode} 为 {@link WorkspaceMode#ISOLATED} 时，
 *       定义目录中的技能、知识和 MEMORY 均可用。
 *   <li><b>远程 HTTP</b> — {@link #getUrl()} 指向 AgentScope 任务 HTTP 服务器。没有本地定义工作区或内联正文；
 *       子代理在进程外运行。与定义工作区和内联正文互斥。
 * </ol>
 *
 * <p>三种源模式互斥：最多只能设置 {@link Builder#workspace(Path)}、非空
 * {@link Builder#inlineAgentsBody(String)} 或非空 {@link Builder#url(String)} 中的一个。
 *
 * <p>工作区解析遵循 {@link WorkspaceMode} 中的五行决策表。
 *
 * <p>{@code tools} 列表在非空时充当继承父工具的<em>白名单过滤器</em>：
 * 仅保留名称在列表中的继承工具。子代理本地的工具注册仍可由子代理构建器添加。
 *
 * <p>通过 {@link #builder()} 获取实例。
 *
 * <p>示例（编程方式）：
 *
 * <pre>{@code
 * SubagentDeclaration decl = SubagentDeclaration.builder()
 *     .name("code-reviewer")
 *     .description("Reviews code for security, performance, and readability issues.")
 *     .workspace(Path.of("./defs/code-reviewer"))
 *     .workspaceMode(WorkspaceMode.ISOLATED)
 *     .model("qwen3-max")
 *     .tools(List.of("read_file", "grep_files", "edit_file"))
 *     .build();
 * }</pre>
 */
public final class SubagentDeclaration {

    private final String name;
    private final String description;
    private final WorkspaceMode workspaceMode;
    private final Path workspacePath;
    private final String inlineAgentsBody;
    private final String model;
    private final int maxIters;
    private final List<String> tools;

    /** Base URL of the remote task server (e.g. {@code http://host:8080}). */
    private final String url;

    private final Map<String, String> headers;

    private SubagentDeclaration(Builder b) {
        this.name = b.name;
        this.description = b.description;
        this.workspaceMode = b.workspaceMode;
        this.workspacePath = b.workspacePath;
        this.inlineAgentsBody = b.inlineAgentsBody;
        this.model = b.model;
        this.maxIters = b.maxIters;
        this.tools = b.tools != null ? List.copyOf(b.tools) : List.of();
        this.url = b.url;
        this.headers = b.headers != null && !b.headers.isEmpty() ? Map.copyOf(b.headers) : null;
    }

    /** Factory method for a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Unique name / agent-id used to reference this subagent. */
    public String getName() {
        return name;
    }

    /** Human-readable description; the main agent uses this to decide when to delegate. */
    public String getDescription() {
        return description;
    }

    /**
     * Workspace resolution strategy. Defaults to {@link WorkspaceMode#ISOLATED} when not
     * specified.
     */
    public WorkspaceMode getWorkspaceMode() {
        return workspaceMode;
    }

    /**
     * Path to the definition workspace directory (contains at least {@code AGENTS.md}). When
     * {@code null} this declaration is in inline mode and {@link #getInlineAgentsBody()} provides
     * the system prompt.
     */
    public Path getWorkspacePath() {
        return workspacePath;
    }

    /**
     * Inline system-prompt body used when {@link #getWorkspacePath()} is {@code null}. May be
     * {@code null} or blank if neither a definition workspace nor an inline body is provided.
     */
    public String getInlineAgentsBody() {
        return inlineAgentsBody;
    }

    /**
     * Optional model override (e.g. {@code "qwen3-max"} or {@code "openai:gpt-4o-mini"}). When
     * {@code null} or blank, the parent model is used.
     */
    public String getModel() {
        return model;
    }

    /** Maximum reasoning iterations. Defaults to 10. */
    public int getMaxIters() {
        return maxIters;
    }

    /**
     * Optional tool allowlist. When non-empty, only inherited parent tools whose names are listed
     * remain on the subagent's inherited toolkit. Empty means inherit all parent tools.
     */
    public List<String> getTools() {
        return tools;
    }

    /** Returns {@code true} when this declaration targets a remote task HTTP server. */
    public boolean isRemote() {
        return url != null && !url.isBlank();
    }

    /**
     * Base URL of the remote task server. Non-blank only in {@linkplain #isRemote() remote} mode.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Optional HTTP headers (e.g. auth) sent to the remote task server. Never empty when
     * non-null.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** Returns {@code true} when this declaration points at an external definition workspace. */
    public boolean hasDefinitionWorkspace() {
        return workspacePath != null;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {

        private String name;
        private String description;
        private WorkspaceMode workspaceMode = WorkspaceMode.ISOLATED;
        private Path workspacePath;
        private String inlineAgentsBody;
        private String model;
        private int maxIters = 10;
        private List<String> tools;
        private String url;
        private Map<String, String> headers;

        private Builder() {}

        /** Sets the unique name / agent-id for this subagent (required). */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the human-readable description the orchestrator uses to decide when to delegate
         * (required).
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the workspace resolution mode. Defaults to {@link WorkspaceMode#ISOLATED}.
         *
         * @param mode workspace mode; {@code null} is treated as {@link WorkspaceMode#ISOLATED}
         */
        public Builder workspaceMode(WorkspaceMode mode) {
            this.workspaceMode = mode != null ? mode : WorkspaceMode.ISOLATED;
            return this;
        }

        /**
         * Points this declaration at an external definition workspace.
         *
         * <p>Mutually exclusive with {@link #inlineAgentsBody(String)}: passing both a non-null
         * path <em>and</em> a non-blank inline body will cause {@link #build()} to throw.
         *
         * @param workspacePath absolute path, or path relative to {@code mainWorkspace} when set
         *     via a Markdown front matter file
         */
        public Builder workspace(Path workspacePath) {
            this.workspacePath = workspacePath;
            return this;
        }

        /**
         * Sets the inline system-prompt body for lightweight subagents that do not need a
         * dedicated definition workspace.
         *
         * <p>Mutually exclusive with {@link #workspace(Path)}.
         *
         * @param body the system-prompt body text (Markdown); may be {@code null} or blank
         */
        public Builder inlineAgentsBody(String body) {
            this.inlineAgentsBody = body;
            return this;
        }

        /**
         * Optional model override resolved via {@link io.agentscope.core.model.ModelRegistry}.
         * Falls back to the parent model when blank or unresolvable.
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** Maximum reasoning iterations (default 10). */
        public Builder maxIters(int maxIters) {
            this.maxIters = maxIters;
            return this;
        }

        /**
         * Tool allowlist: when non-empty, only inherited parent tools with listed names are kept.
         * Child-local tool registrations are unaffected.
         */
        public Builder tools(List<String> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Remote task server base URL. Mutually exclusive with {@link #workspace(Path)} and a
         * non-blank {@link #inlineAgentsBody(String)}.
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Optional HTTP headers for the remote task server (e.g. {@code Authorization}). Only used
         * when {@link #url(String)} is set.
         */
        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * Builds the {@link SubagentDeclaration}.
         *
         * @throws IllegalArgumentException if {@code name} or {@code description} is blank, or
         *     mutually exclusive fields are combined (workspace vs inline vs remote URL)
         */
        public SubagentDeclaration build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("SubagentDeclaration requires a non-blank name");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException(
                        "SubagentDeclaration requires a non-blank description");
            }
            boolean remote = url != null && !url.isBlank();
            if (remote) {
                if (workspacePath != null) {
                    throw new IllegalArgumentException(
                            "url() and workspace(Path) are mutually exclusive for subagent '"
                                    + name
                                    + "'");
                }
                if (inlineAgentsBody != null && !inlineAgentsBody.isBlank()) {
                    throw new IllegalArgumentException(
                            "url() and inlineAgentsBody() are mutually exclusive for subagent '"
                                    + name
                                    + "'");
                }
            } else if (workspacePath != null
                    && inlineAgentsBody != null
                    && !inlineAgentsBody.isBlank()) {
                throw new IllegalArgumentException(
                        "workspace(Path) and inlineAgentsBody() are mutually exclusive;"
                                + " set at most one for subagent '"
                                + name
                                + "'");
            }
            return new SubagentDeclaration(this);
        }
    }
}
