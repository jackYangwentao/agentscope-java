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
package io.agentscope.harness.agent.filesystem.spec;

import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.CompositeFilesystem;
import io.agentscope.harness.agent.filesystem.OverlayFilesystem;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystemWithShell;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.store.BaseStore;
import io.agentscope.harness.agent.store.NamespaceFactory;
import io.agentscope.harness.agent.workspace.WorkspaceIndex;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 非沙箱"组合"文件系统模式的规范。
 *
 * <p>此规范生成一个 {@link CompositeFilesystem}，它混合了：
 *
 * <ul>
 *   <li>用于工作空间本地、非托管文件的普通 {@link LocalFilesystem}（无 shell）；
 *   <li>用于跨节点路径（memory、skills、subagents、knowledge、sessions、tasks）的
 *       每路由 {@link RemoteFilesystem} 实例。每个路由都有自己的存储命名空间段，
 *       以防止路由间的键冲突。
 * </ul>
 *
 * <p>由于默认后端是 {@link LocalFilesystem}（而非 {@link LocalFilesystemWithShell}），
 * 此模式中有意不提供 shell 执行能力——如果需要 shell，请使用沙箱文件系统规范或
 * {@link LocalFilesystemWithShell}。
 *
 * <p>默认共享路由（每个路由获得独立的存储命名空间段）：
 *
 * <ul>
 *   <li>{@code AGENTS.md}, {@code MEMORY.md} → 段 {@code root}
 *   <li>{@code memory/} → 段 {@code memory}
 *   <li>{@code skills/} → 段 {@code skills}
 *   <li>{@code subagents/} → 段 {@code subagents}
 *   <li>{@code knowledge/} → 段 {@code knowledge}
 *   <li>{@code agents/<agentId>/sessions/} → 段 {@code sessions}
 *   <li>{@code agents/<agentId>/tasks/} → 段 {@code tasks}
 * </ul>
 *
 * <p>共享文件的存储命名空间由 {@link #isolationScope(IsolationScope)} 控制，
 * 它镜像了沙箱隔离语义：
 *
 * <ul>
 *   <li>{@link IsolationScope#SESSION} — 每个会话的命名空间</li>
 *   <li>{@link IsolationScope#USER}（默认）— 每个用户的命名空间，跨会话共享</li>
 *   <li>{@link IsolationScope#AGENT} — 每个代理的命名空间，跨所有用户共享</li>
 *   <li>{@link IsolationScope#GLOBAL} — 单个全局命名空间</li>
 * </ul>
 */
public class RemoteFilesystemSpec {

    private final BaseStore store;
    private final Set<String> extraSharedPrefixes = new LinkedHashSet<>();
    private String anonymousUserId = "_default";
    private IsolationScope isolationScope = IsolationScope.USER;
    private WorkspaceIndex workspaceIndex = null;

    public RemoteFilesystemSpec(BaseStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    /**
     * 添加一个路由到共享存储的额外工作空间相对前缀。
     *
     * <p>示例：{@code knowledge/}、{@code prompts/}。
     */
    public RemoteFilesystemSpec addSharedPrefix(String prefix) {
        if (prefix != null && !prefix.isBlank()) {
            extraSharedPrefixes.add(normalizePrefix(prefix));
        }
        return this;
    }

    /**
     * 设置当运行时 {@code userId} 缺失/空白时的回退用户标识符。
     */
    public RemoteFilesystemSpec anonymousUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("anonymous user id must not be blank");
        }
        this.anonymousUserId = userId;
        return this;
    }

    /**
     * 设置控制共享文件存储命名空间的隔离范围。
     *
     * <p>镜像沙箱 {@link io.agentscope.harness.agent.sandbox.SandboxContext} 隔离语义。
     * 默认为 {@link IsolationScope#USER}。
     *
     * @param scope 隔离范围
     * @return 此规范
     */
    public RemoteFilesystemSpec isolationScope(IsolationScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("isolation scope must not be null");
        }
        this.isolationScope = scope;
        return this;
    }

    /**
     * 设置用于加速远程文件系统读取（ls/glob/exists/grep）的工作空间索引。
     * 如果未设置，远程文件系统将回退到完整存储扫描。
     */
    public RemoteFilesystemSpec workspaceIndex(WorkspaceIndex index) {
        this.workspaceIndex = index;
        return this;
    }

    /**
     *
     * <ul>
     *   <li>default backend: {@link LocalFilesystem} (no shell), per-user namespaced
     *   <li>shared <b>prefix</b> routes ({@code memory/}, {@code skills/}, {@code subagents/},
     *       {@code knowledge/}, {@code agents/<id>/sessions/}, {@code agents/<id>/tasks/}, plus
     *       any {@code addSharedPrefix} extras): wrapped in an {@link OverlayFilesystem} where
     *       the <em>upper</em> layer is the {@link RemoteFilesystem} (per-user, persisted in the
     *       {@link BaseStore}) and the <em>lower</em> layer is a read-only {@link LocalFilesystem}
     *       rooted at {@code workspace.resolve(<routeDir>)}. So scaffolded template content under
     *       {@code <workspace>/skills/}, {@code <workspace>/subagents/}, etc. is visible as a
     *       baseline; per-user edits land in the remote store via copy-on-write and override the
     *       template on subsequent reads.
     *   <li>{@code AGENTS.md}, {@code MEMORY.md}, {@code tools.json} exact-file routes: wrapped
     *       in an {@link OverlayFilesystem} where the <em>upper</em> is the {@code root}-segment
     *       {@link RemoteFilesystem} and the <em>lower</em> is a read-only {@link LocalFilesystem}
     *       rooted at the workspace, so the scaffolded template files at the workspace root are
     *       visible as the baseline. {@link CompositeFilesystem} does not recurse into exact-file
     *       routes when listing/globbing the tree; it performs a single {@code exists} check
     *       against the overlay, which is satisfied by either layer.
     * </ul>
     */
    public AbstractFilesystem toFilesystem(
            Path workspace, String agentId, NamespaceFactory localNamespaceFactory) {
        String effectiveAgentId = agentId == null || agentId.isBlank() ? "HarnessAgent" : agentId;
        AbstractFilesystem local = new LocalFilesystem(workspace, false, 10, localNamespaceFactory);

        // Read-only workspace-root template view for the exact-file overlays below. The lower
        // technically exposes the entire workspace, but CompositeFilesystem does not recurse into
        // exact-file routes (it does single-key exists/read), so the over-exposure is unreachable.
        LocalFilesystem workspaceTemplate = new LocalFilesystem(workspace, true, 10, null);

        Map<String, AbstractFilesystem> routes = new LinkedHashMap<>();
        routes.put("AGENTS.md", exactFileOverlay("root", effectiveAgentId, workspaceTemplate));
        routes.put("MEMORY.md", exactFileOverlay("root", effectiveAgentId, workspaceTemplate));
        routes.put("tools.json", exactFileOverlay("root", effectiveAgentId, workspaceTemplate));
        routes.put(
                "memory/", overlayRoute(workspace.resolve("memory"), "memory", effectiveAgentId));
        routes.put(
                "skills/", overlayRoute(workspace.resolve("skills"), "skills", effectiveAgentId));
        routes.put(
                "subagents/",
                overlayRoute(workspace.resolve("subagents"), "subagents", effectiveAgentId));
        routes.put(
                "knowledge/",
                overlayRoute(workspace.resolve("knowledge"), "knowledge", effectiveAgentId));
        routes.put(
                "agents/" + effectiveAgentId + "/sessions/",
                overlayRoute(
                        workspace.resolve("agents").resolve(effectiveAgentId).resolve("sessions"),
                        "sessions",
                        effectiveAgentId));
        routes.put(
                "agents/" + effectiveAgentId + "/tasks/",
                overlayRoute(
                        workspace.resolve("agents").resolve(effectiveAgentId).resolve("tasks"),
                        "tasks",
                        effectiveAgentId));
        for (String extra : extraSharedPrefixes) {
            String segment = routeSegmentFromPrefix(extra);
            routes.put(extra, overlayRoute(workspace.resolve(segment), segment, effectiveAgentId));
        }
        return new CompositeFilesystem(local, routes);
    }

    /**
     * 为工作空间前缀路由构建 {@link OverlayFilesystem}。上层是由 {@link BaseStore} 支持的
     * 每用户 {@link RemoteFilesystem}；下层是一个只读的 {@link LocalFilesystem}，根目录为
     * {@code localTemplateDir}，使得脚手架模板内容作为基线可见。下层启用 {@code virtualMode=true}，
     * 以便报告锚定到自身根的路径，这正是 {@link CompositeFilesystem} 路由重映射所期望的。
     */
    private OverlayFilesystem overlayRoute(
            Path localTemplateDir, String routeSegment, String agentId) {
        RemoteFilesystem upper = remoteForRoute(routeSegment, agentId);
        LocalFilesystem lower = new LocalFilesystem(localTemplateDir, true, 10, null);
        return new OverlayFilesystem(upper, lower);
    }

    /**
     * 为精确文件路由（例如 {@code AGENTS.md}）构建 {@link OverlayFilesystem}。
     * 上层是在 {@code root} 命名空间段上的每用户 {@link RemoteFilesystem}；
     * 下层是共享的工作空间根 {@link LocalFilesystem}，使得脚手架模板文件
     * （{@code workspace/<filename>}）作为基线可见。
     */
    private OverlayFilesystem exactFileOverlay(
            String routeSegment, String agentId, LocalFilesystem workspaceTemplate) {
        RemoteFilesystem upper = remoteForRoute(routeSegment, agentId);
        return new OverlayFilesystem(upper, workspaceTemplate);
    }

    private RemoteFilesystem remoteForRoute(String routeSegment, String agentId) {
        NamespaceFactory base = storeNamespace(agentId);
        NamespaceFactory extended =
                rc -> {
                    List<String> ns = new ArrayList<>(base.getNamespace(rc));
                    ns.add(routeSegment);
                    return ns;
                };
        return new RemoteFilesystem(store, extended).withIndex(workspaceIndex);
    }

    private static String routeSegmentFromPrefix(String normalizedPrefix) {
        String segment = normalizedPrefix;
        while (segment.endsWith("/")) {
            segment = segment.substring(0, segment.length() - 1);
        }
        return segment.isEmpty() ? "extra" : segment;
    }

    private NamespaceFactory storeNamespace(String agentId) {
        return rc -> {
            String uid = rc != null ? rc.getUserId() : null;
            String sid = rc != null ? rc.getSessionId() : null;

            return switch (isolationScope) {
                case SESSION -> {
                    String effectiveSid = (sid != null && !sid.isBlank()) ? sid : "default";
                    yield List.of("agents", agentId, "sessions", effectiveSid);
                }
                case USER -> {
                    String effectiveUid = (uid != null && !uid.isBlank()) ? uid : anonymousUserId;
                    yield List.of("agents", agentId, "users", effectiveUid);
                }
                case AGENT -> List.of("agents", agentId, "shared");
                case GLOBAL -> List.of("global");
            };
        };
    }

    private static String normalizePrefix(String prefix) {
        String normalized = prefix.replace('\\', '/').strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
