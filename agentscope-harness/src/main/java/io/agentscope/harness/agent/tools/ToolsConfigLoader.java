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
package io.agentscope.harness.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.workspace.WorkspaceConstants;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将 {@code workspace/tools.json} 加载为 {@link ToolsConfig}。
 * Loads {@code workspace/tools.json} into a {@link ToolsConfig}.
 *
 * <p>通过 {@link WorkspaceManager#readManagedWorkspaceFileUtf8} 读取，
 * 使文件系统覆盖（沙箱/远程）路径的行为与 {@code AGENTS.md} 一致。
 * 在解析前对原始 JSON 文本执行 {@code ${ENV_VAR}} 环境变量替换，
 * 使头部、环境条目、URL 等中的密钥不保留在文件中。
 * 解析失败时不会抛出异常——会记录日志并返回 {@link Optional#empty()}，
 * 确保代理仍能使用其默认工具包正常构建。
 * Reads through {@link WorkspaceManager#readManagedWorkspaceFileUtf8} so that the
 * filesystem-overlay (sandbox / remote) path is honoured exactly like {@code AGENTS.md}. Performs
 * {@code ${ENV_VAR}} substitution against {@link System#getenv} on the raw JSON text before
 * parsing so that secrets in headers, env entries, urls, etc. are kept out of the file. Parse
 * failures never throw — they log and return {@link Optional#empty()} so the agent still builds
 * with its default toolkit.
 */
public final class ToolsConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ToolsConfigLoader.class);

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Pattern ENV_VAR = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private ToolsConfigLoader() {}

    /**
     * 读取由 {@code wsManager} 管理的工作空间中的 {@code tools.json} 文件。
     * 当文件缺失、空白、不可读（例如在调用上下文存在前访问沙箱文件系统）或无法解析时
     * 返回 {@link Optional#empty()}。
     * Reads {@code tools.json} relative to the workspace managed by {@code wsManager}. Returns
     * {@link Optional#empty()} when the file is missing, blank, unreadable (e.g. sandbox
     * filesystem accessed before its call context exists), or unparseable.
     */
    public static Optional<ToolsConfig> load(WorkspaceManager wsManager) {
        if (wsManager == null) {
            return Optional.empty();
        }
        String raw;
        try {
            raw =
                    wsManager.readManagedWorkspaceFileUtf8(
                            RuntimeContext.empty(), WorkspaceConstants.TOOLS_JSON);
        } catch (Exception e) {
            // 沙箱/远程文件系统在构建时可能不可达。静默回退——代理仍应正常构建。
            log.debug(
                    "Could not read {} via workspace manager ({}); skipping.",
                    WorkspaceConstants.TOOLS_JSON,
                    e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String substituted = substituteEnv(raw);
        try {
            ToolsConfig cfg = MAPPER.readValue(substituted, ToolsConfig.class);
            return Optional.ofNullable(cfg);
        } catch (Exception e) {
            log.warn(
                    "Failed to parse {}: {}. Falling back to default toolkit.",
                    WorkspaceConstants.TOOLS_JSON,
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将 {@code raw} 中的 {@code ${VAR}} 替换为 {@link System#getenv} 的值。
     * 未设置的环境变量解析为空字符串并在 WARN 级别记录。
     * 这对于使 {@code "Authorization": "Bearer ${GITHUB_TOKEN}"} 等头部不保留在工作空间文件中很有用。
     * Replaces {@code ${VAR}} occurrences in {@code raw} with {@link System#getenv} values. Unset
     * variables resolve to an empty string and are logged at WARN. Useful so that headers like
     * {@code "Authorization": "Bearer ${GITHUB_TOKEN}"} can stay out of the workspace file.
     */
    static String substituteEnv(String raw) {
        Matcher m = ENV_VAR.matcher(raw);
        StringBuilder out = new StringBuilder(raw.length());
        Set<String> warned = new HashSet<>();
        while (m.find()) {
            String var = m.group(1);
            String value = System.getenv(var);
            if (value == null) {
                if (warned.add(var)) {
                    log.warn(
                            "tools.json references env var ${{{}}} which is not set; substituting"
                                    + " empty string.",
                            var);
                }
                value = "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
