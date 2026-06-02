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
package io.agentscope.harness.agent.workspace;

/** Path constants for the workspace directory structure. */
/** 工作区目录结构的路径常量。 */
public final class WorkspaceConstants {

    private WorkspaceConstants() {}

    /** 默认工作区根目录路径 */
    public static final String DEFAULT_WORKSPACE_ROOT = ".agentscope/workspace";

    /** AGENTS.md 配置文件 */
    public static final String AGENTS_MD = "AGENTS.md";
    /** MEMORY.md 记忆文件 */
    public static final String MEMORY_MD = "MEMORY.md";
    /** tools.json 工具配置文件 */
    public static final String TOOLS_JSON = "tools.json";

    /** 记忆存储目录 */
    public static final String MEMORY_DIR = "memory";
    /** 技能存储目录 */
    public static final String SKILLS_DIR = "skills";
    /** 知识库目录 */
    public static final String KNOWLEDGE_DIR = "knowledge";
    /** KNOWLEDGE.md 知识库文件 */
    public static final String KNOWLEDGE_MD = "KNOWLEDGE.md";
    /** 规则目录 */
    public static final String RULES_DIR = "rules";

    /** 代理配置目录 */
    public static final String AGENTS_DIR = "agents";
    /** 会话存储目录 */
    public static final String SESSIONS_DIR = "sessions";
    /** 任务存储目录 */
    public static final String TASKS_DIR = "tasks";

    /**
     * Per-agent session store filename under {@code agents/&lt;agentId&gt;/sessions/}
     */
    /**
     * 每个代理的会话存储文件名，位于 {@code agents/&lt;agentId&gt;/sessions/} 下
     */
    public static final String SESSIONS_STORE = "sessions.json";

    /** JSONL session context file extension (LLM-facing, may be compacted). */
    /** JSONL 会话上下文文件扩展名（面向 LLM，可能被压缩）。 */
    public static final String SESSION_CONTEXT_EXT = ".jsonl";

    /** JSONL session log file extension (full history, append-only, never compacted). */
    /** JSONL 会话日志文件扩展名（完整历史记录，仅追加，永不压缩）。 */
    public static final String SESSION_LOG_EXT = ".log.jsonl";
}
