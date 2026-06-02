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
package io.agentscope.harness.agent.filesystem;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.filesystem.model.EditResult;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.GlobResult;
import io.agentscope.harness.agent.filesystem.model.GrepResult;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import io.agentscope.harness.agent.filesystem.model.WriteResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文件系统包装器，在每次委托调用时替换为固定的 {@link RuntimeContext}，忽略调用者提供的上下文。
 *
 * <p>由 {@code HarnessAgent.workspaceFor(userId, sessionId)} 使用，构造绑定到显式用户身份的带外
 * {@link io.agentscope.harness.agent.workspace.WorkspaceManager} 视图。作用于其他用户命名空间的
 * 控制器可以向下游传递 {@link RuntimeContext#empty()}；此包装器确保底层的命名空间工厂仍然接收
 * 预设的身份而不是（空的）调用者上下文。
 */
public final class BakedContextFilesystem implements AbstractFilesystem {

    /** 被委托的实际文件系统实例 */
    private final AbstractFilesystem delegate;
    /** 固定的运行时上下文，所有调用均使用此上下文而非调用者传入的上下文 */
    private final RuntimeContext bakedRc;

    /**
     * 创建一个烘焙上下文文件系统。
     *
     * @param delegate 实际执行文件操作的下游文件系统
     * @param bakedRc 固定的运行时上下文；如果为 null 则使用 {@link RuntimeContext#empty()}
     */
    public BakedContextFilesystem(AbstractFilesystem delegate, RuntimeContext bakedRc) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.bakedRc = bakedRc != null ? bakedRc : RuntimeContext.empty();
    }

    @Override
    public LsResult ls(RuntimeContext runtimeContext, String path) {
        return delegate.ls(bakedRc, path);
    }

    @Override
    public ReadResult read(RuntimeContext runtimeContext, String filePath, int offset, int limit) {
        return delegate.read(bakedRc, filePath, offset, limit);
    }

    @Override
    public WriteResult write(RuntimeContext runtimeContext, String filePath, String content) {
        return delegate.write(bakedRc, filePath, content);
    }

    @Override
    public EditResult edit(
            RuntimeContext runtimeContext,
            String filePath,
            String oldString,
            String newString,
            boolean replaceAll) {
        return delegate.edit(bakedRc, filePath, oldString, newString, replaceAll);
    }

    @Override
    public GrepResult grep(
            RuntimeContext runtimeContext, String pattern, String path, String glob) {
        return delegate.grep(bakedRc, pattern, path, glob);
    }

    @Override
    public GlobResult glob(RuntimeContext runtimeContext, String pattern, String path) {
        return delegate.glob(bakedRc, pattern, path);
    }

    @Override
    public List<FileUploadResponse> uploadFiles(
            RuntimeContext runtimeContext, List<Map.Entry<String, byte[]>> files) {
        return delegate.uploadFiles(bakedRc, files);
    }

    @Override
    public List<FileDownloadResponse> downloadFiles(
            RuntimeContext runtimeContext, List<String> paths) {
        return delegate.downloadFiles(bakedRc, paths);
    }

    @Override
    public WriteResult delete(RuntimeContext runtimeContext, String path) {
        return delegate.delete(bakedRc, path);
    }

    @Override
    public WriteResult move(RuntimeContext runtimeContext, String fromPath, String toPath) {
        return delegate.move(bakedRc, fromPath, toPath);
    }

    @Override
    public boolean exists(RuntimeContext runtimeContext, String path) {
        return delegate.exists(bakedRc, path);
    }
}
