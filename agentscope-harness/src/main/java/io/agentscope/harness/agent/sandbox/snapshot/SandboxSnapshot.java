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
package io.agentscope.harness.agent.sandbox.snapshot;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.InputStream;

/**
 * 表示沙箱工作区的持久化快照。
 * <p>
 * 快照允许在会话停止之间保留工作区状态，并在后续会话启动时恢复。
 * 实现决定了快照存储的位置和方式：
 * 本地磁盘（{@link LocalSandboxSnapshot}）、远程存储（{@link RemoteSandboxSnapshot}），
 * 或不存储（{@link NoopSandboxSnapshot}）。
 * <p>
 * Represents a persisted snapshot of a sandbox workspace.
 *
 * <p>Snapshots allow workspace state to be preserved between session stops and restored on
 * subsequent session starts. Implementations determine where and how the snapshot is stored:
 * local disk ({@link LocalSandboxSnapshot}), remote storage ({@link RemoteSandboxSnapshot}),
 * or not at all ({@link NoopSandboxSnapshot}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NoopSandboxSnapshot.class, name = "noop"),
    @JsonSubTypes.Type(value = LocalSandboxSnapshot.class, name = "local"),
    @JsonSubTypes.Type(value = RemoteSandboxSnapshot.class, name = "remote"),
})
public interface SandboxSnapshot {

    /**
     * 将工作区归档持久化到此快照。
     * <p>
     * Persists the workspace archive to this snapshot.
     *
     * @param workspaceArchive 要持久化的工作区 tar 流；调用者在此调用后负责关闭流
     * @throws Exception 如果持久化操作失败
     */
    void persist(InputStream workspaceArchive) throws Exception;

    /**
     * 从此快照恢复工作区归档。
     * <p>
     * Restores the workspace archive from this snapshot.
     *
     * @return 工作区的 tar 流；调用者负责关闭
     * @throws Exception 如果恢复操作失败或快照不可恢复
     */
    InputStream restore() throws Exception;

    /**
     * 返回此快照当前是否可恢复。
     * <p>
     * Returns whether this snapshot can currently be restored.
     *
     * @return 如果 {@link #restore()} 会成功则返回 true
     * @throws Exception 如果检查可恢复性失败
     */
    boolean isRestorable() throws Exception;

    /**
     * 返回此快照的唯一标识符。
     * <p>
     * Returns the unique identifier for this snapshot.
     *
     * @return 快照 ID
     */
    String getId();

    /**
     * 返回 JSON 序列化中使用的快照类型鉴别器。
     * <p>
     * Returns the snapshot type discriminator used in JSON serialization.
     *
     * @return 类型字符串（例如 "noop"、"local"、"remote"）
     */
    String getType();

    /**
     * 返回此快照是否实际持久化数据。
     * <p>
     * 当为 {@code false} 时，
     * {@link io.agentscope.harness.agent.sandbox.AbstractBaseSandbox#stop()} 会完全跳过潜在昂贵的
     * 工作区归档步骤。默认为 {@code true}。
     * <p>
     * Returns whether this snapshot actually persists data.
     *
     * <p>When {@code false}, {@link io.agentscope.harness.agent.sandbox.AbstractBaseSandbox#stop()}
     * skips the potentially expensive workspace archive step entirely. Defaults to {@code true}.
     *
     * @return 仅对丢弃所有归档数据的空操作实现返回 false
     */
    default boolean isPersistenceEnabled() {
        return true;
    }
}
