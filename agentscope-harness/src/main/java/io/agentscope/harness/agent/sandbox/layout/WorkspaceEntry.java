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
package io.agentscope.harness.agent.sandbox.layout;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * {@link io.agentscope.harness.agent.sandbox.WorkspaceSpec} 中条目的基类。
 * <p>
 * 每个条目描述了沙箱启动时工作区中应存在的单个文件或目录。
 * 条目可以从内联内容、本地主机文件或远程源中构建。
 * <p>
 * 当 {@code ephemeral} 为 {@code true} 时，即使存在快照，此条目也会在每次沙箱启动时
 * 重新应用。非临时（non-ephemeral）条目会持久化在快照内部，并在恢复时随工作区一起还原。
 * <p>
 * Base class for entries in a {@link io.agentscope.harness.agent.sandbox.WorkspaceSpec}.
 *
 * <p>Each entry describes a single file or directory that should be present in the sandbox
 * workspace at startup. Entries can be materialized from inline content, local host files, or
 * remote sources.
 *
 * <p>When {@code ephemeral} is {@code true}, this entry is always re-applied on every sandbox
 * start, even when a snapshot exists. Non-ephemeral entries are persisted inside the snapshot
 * and restored with the workspace.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FileEntry.class, name = "file"),
    @JsonSubTypes.Type(value = DirEntry.class, name = "dir"),
    @JsonSubTypes.Type(value = LocalFileEntry.class, name = "local_file"),
    @JsonSubTypes.Type(value = LocalDirEntry.class, name = "local_dir"),
    @JsonSubTypes.Type(value = GitRepoEntry.class, name = "git_repo"),
    @JsonSubTypes.Type(value = WorkspaceProjectionEntry.class, name = "workspace_projection"),
    @JsonSubTypes.Type(value = BindMountEntry.class, name = "bind_mount"),
})
public abstract class WorkspaceEntry {

    private boolean ephemeral = false;

    /**
     * 返回此条目是否为临时性（ephemeral）。
     * <p>
     * 临时条目在会话恢复时无论是否存在快照都会重新应用。
     * 适用于不应被快照的动态配置。
     * <p>
     * Returns whether this entry is ephemeral.
     *
     * <p>Ephemeral entries are always re-applied on session resume regardless of whether a
     * snapshot exists. They are suitable for dynamic configuration that should not be snapshotted.
     *
     * @return 如果此条目是临时性的则返回 true
     */
    public boolean isEphemeral() {
        return ephemeral;
    }

    /**
     * 设置此条目是否为临时性。
     * <p>
     * Sets whether this entry is ephemeral.
     *
     * @param ephemeral 标记为临时性则设为 true
     */
    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
}
