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

import io.agentscope.harness.agent.sandbox.SandboxException;
import java.io.InputStream;

/**
 * 由 {@link RemoteSnapshotClient}（例如 S3、OSS、GCS）支持的快照。
 * <p>
 * 此类将所有操作委托给提供的客户端。客户端负责认证、重试逻辑和网络错误处理。
 * <p>
 * 注意：{@code RemoteSandboxSnapshot} 不能直接序列化为 JSON，因为
 * {@link RemoteSnapshotClient} 无法被序列化。持久化会话状态时，
 * 只需要 {@code id} —— 客户端在恢复时从构建器重新注入。
 * <p>
 * Snapshot backed by a {@link RemoteSnapshotClient} (e.g. S3, OSS, GCS).
 *
 * <p>This class delegates all operations to the provided client. The client is responsible
 * for authentication, retry logic, and network error handling.
 *
 * <p>Note: {@code RemoteSandboxSnapshot} is not directly serializable to JSON because
 * {@link RemoteSnapshotClient} cannot be serialized. When persisting session state,
 * only the {@code id} is needed — the client is re-injected from the builder at resume time.
 */
public class RemoteSandboxSnapshot implements SandboxSnapshot {

    private final RemoteSnapshotClient client;
    private final String id;

    /**
     * 创建远程快照。
     * <p>
     * Creates a remote snapshot.
     *
     * @param client 要委托操作的远程存储客户端
     * @param id     此快照的唯一标识符
     */
    public RemoteSandboxSnapshot(RemoteSnapshotClient client, String id) {
        this.client = client;
        this.id = id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过 {@link RemoteSnapshotClient#upload} 上传归档。
     * <p>
     * Uploads the archive via {@link RemoteSnapshotClient#upload}.
     */
    @Override
    public void persist(InputStream workspaceArchive) throws Exception {
        try {
            client.upload(id, workspaceArchive);
        } catch (Exception e) {
            throw new SandboxException.SnapshotException(id, "Remote upload failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过 {@link RemoteSnapshotClient#download} 下载归档。
     * <p>
     * Downloads the archive via {@link RemoteSnapshotClient#download}.
     */
    @Override
    public InputStream restore() throws Exception {
        try {
            return client.download(id);
        } catch (Exception e) {
            throw new SandboxException.SnapshotException(id, "Remote download failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>通过 {@link RemoteSnapshotClient#exists} 检查存在性。
     * <p>
     * Checks existence via {@link RemoteSnapshotClient#exists}.
     */
    @Override
    public boolean isRestorable() throws Exception {
        try {
            return client.exists(id);
        } catch (Exception e) {
            throw new SandboxException.SnapshotException(id, "Remote exists check failed", e);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return "remote";
    }
}
