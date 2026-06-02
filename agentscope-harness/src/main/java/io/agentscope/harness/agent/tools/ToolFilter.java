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

import io.agentscope.core.tool.Toolkit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用 {@code workspace/tools.json} 中 {@link ToolsConfig#getAllow() allow} /
 * {@link ToolsConfig#getDeny() deny} 列表对 {@link Toolkit} 已注册工具的过滤。
 * Applies the {@link ToolsConfig#getAllow() allow} / {@link ToolsConfig#getDeny() deny} lists from
 * {@code workspace/tools.json} against a {@link Toolkit}'s registered tools.
 *
 * <p>语义：Semantics:
 *
 * <ul>
 *   <li>当 {@code allow} 非空时，仅保留名称出现在其中的工具。
 *   <li>无论 {@code allow} 如何，{@code deny} 中的条目始终被移除。
 *   <li>两者都为空/不存在时，工具包保持不变。
 * </ul>
 *
 * <p>不对应当前注册工具的名称会在 WARN 级别记录，但被忽略——
 * 工作空间文件中的拼写错误不应中止代理。
 * Names that don't correspond to a currently registered tool are logged at WARN and otherwise
 * ignored — typos in the workspace file should not abort the agent.
 */
public final class ToolFilter {

    private static final Logger log = LoggerFactory.getLogger(ToolFilter.class);

    private ToolFilter() {}

    /**
     * 从 {@code toolkit} 中移除被 {@code cfg} 的 allow/deny 列表排除的工具。
     * 当 {@code cfg} 为 {@code null} 或没有 allow/deny 条目时无操作。
     */
    public static void apply(Toolkit toolkit, ToolsConfig cfg) {
        if (toolkit == null || cfg == null) {
            return;
        }
        List<String> allow = cfg.getAllow();
        List<String> deny = cfg.getDeny();
        boolean allowSet = allow != null && !allow.isEmpty();
        boolean denySet = deny != null && !deny.isEmpty();
        if (!allowSet && !denySet) {
            return;
        }

        Set<String> registered = new LinkedHashSet<>(toolkit.getToolNames());
        Set<String> allowSetView = allowSet ? new HashSet<>(allow) : null;
        Set<String> denySetView = denySet ? new HashSet<>(deny) : null;

        if (allowSetView != null) {
            warnUnknown(allowSetView, registered, "allow");
        }
        if (denySetView != null) {
            warnUnknown(denySetView, registered, "deny");
        }

        // 计算需要移除的工具集合
        Set<String> toRemove = new LinkedHashSet<>();
        for (String name : registered) {
            boolean keep = allowSetView == null || allowSetView.contains(name);
            if (denySetView != null && denySetView.contains(name)) {
                keep = false;
            }
            if (!keep) {
                toRemove.add(name);
            }
        }
        for (String name : toRemove) {
            toolkit.removeTool(name);
        }

        Set<String> remaining = new TreeSet<>(toolkit.getToolNames());
        log.info(
                "tools.json filter applied: removed {} tool(s); {} tool(s) remain: {}",
                toRemove.size(),
                remaining.size(),
                remaining);
    }

    /** 记录在 allow/deny 列表中但未注册的工具名称的警告。 */
    private static void warnUnknown(Set<String> declared, Set<String> registered, String which) {
        for (String name : declared) {
            if (!registered.contains(name)) {
                log.warn(
                        "tools.json '{}' references unknown tool '{}' (not currently registered).",
                        which,
                        name);
            }
        }
    }
}
