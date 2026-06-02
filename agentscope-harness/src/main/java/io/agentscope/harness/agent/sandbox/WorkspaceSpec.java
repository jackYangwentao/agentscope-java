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
package io.agentscope.harness.agent.sandbox;

import io.agentscope.harness.agent.sandbox.layout.WorkspaceEntry;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 描述沙箱工作空间的期望初始状态：根路径、实体化的文件/目录条目以及每次执行的环境变量。
 * Describes the desired initial state of a sandbox workspace: root path, materialized
 * file/directory entries, and per-exec environment variables.
 *
 * <p>字段说明：Fields:
 * <ul>
 *   <li>{@code root} — 沙箱内的工作空间路径（默认：{@code /workspace}）
 *   <li>{@code entries} — 启动时应用的文件、目录和可选的 {@code bind_mount} 条目
 *       （bind mount 由 Docker/Kubernetes 后端强制执行，不作为复制的文件实体化）
 *   <li>{@code environment} — 注入到每个 exec 命令的环境变量
 * </ul>
 *
 * <p>使用示例：Usage example:
 * <pre>{@code
 * WorkspaceSpec spec = new WorkspaceSpec();
 * spec.setRoot("/workspace");
 * spec.getEntries().put("README.md", new io.agentscope.harness.agent.sandbox.layout.FileEntry("# My Project"));
 * spec.getEnvironment().put("DEBUG", "true");
 * }</pre>
 */
public class WorkspaceSpec {

    private String root = "/workspace";
    private Map<String, WorkspaceEntry> entries = new LinkedHashMap<>();
    private Map<String, String> environment = new LinkedHashMap<>();

    public WorkspaceSpec() {}

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public Map<String, WorkspaceEntry> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, WorkspaceEntry> entries) {
        this.entries = entries != null ? entries : new LinkedHashMap<>();
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment != null ? environment : new LinkedHashMap<>();
    }

    /**
     * 创建深拷贝。entries 映射中的 {@link WorkspaceEntry} 值是共享的（浅拷贝）；
     * 一旦沙箱启动，它们被视为不可变。
     * Creates a deep copy. {@link WorkspaceEntry} values in the entries map are shared
     * (shallow copy); they are treated as immutable once a sandbox has started.
     */
    public WorkspaceSpec copy() {
        WorkspaceSpec copy = new WorkspaceSpec();
        copy.root = this.root;
        copy.entries = new LinkedHashMap<>(this.entries);
        copy.environment = new LinkedHashMap<>(this.environment);
        return copy;
    }
}
