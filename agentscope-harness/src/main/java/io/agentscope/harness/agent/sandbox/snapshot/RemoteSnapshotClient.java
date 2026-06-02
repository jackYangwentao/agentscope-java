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

import java.io.InputStream;

/**
 * 用户实现的接口，用于向/从远程存储（例如 S3、OSS、GCS 或自定义 blob 存储）上传和下载快照归档。
 * <p>
 * 实现此接口并将其传递给 {@link RemoteSnapshotSpec} 以启用远程快照存储。
 * 实现负责认证、重试逻辑和连接管理。
 * <p>
 * User-implemented interface for uploading and downloading snapshot archives to/from remote
 * storage (e.g. S3, OSS, GCS, or a custom blob store).
 *
 * <p>Implement this interface and pass it to {@link RemoteSnapshotSpec} to enable
 * remote snapshot storage. The implementation is responsible for authentication,
 * retry logic, and connection management.
 */
public interface RemoteSnapshotClient {

    /**
     * 将快照归档上传到远程存储。
     * <p>
     * Uploads a snapshot archive to remote storage.
     *
     * @param snapshotId 此快照的唯一标识符
     * @param data       要上传的工作区 tar 归档流
     * @throws Exception 如果上传失败
     */
    void upload(String snapshotId, InputStream data) throws Exception;

    /**
     * 从远程存储下载快照归档。
     * <p>
     * Downloads a snapshot archive from remote storage.
     *
     * @param snapshotId 要下载的快照的唯一标识符
     * @return 下载的 tar 归档的 {@link InputStream}
     * @throws Exception 如果下载失败或快照不存在
     */
    InputStream download(String snapshotId) throws Exception;

    /**
     * 检查远程存储中是否存在具有指定 ID 的快照。
     * <p>
     * Checks whether a snapshot with the given ID exists in remote storage.
     *
     * @param snapshotId 要检查的唯一标识符
     * @return 如果快照存在且可下载则返回 {@code true}
     * @throws Exception 如果检查失败
     */
    boolean exists(String snapshotId) throws Exception;
}
