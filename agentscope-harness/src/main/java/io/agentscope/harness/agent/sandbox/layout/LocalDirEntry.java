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

/**
 * 从主机文件系统递归复制目录到沙箱工作区的布局条目。
 * <p>
 * {@code sourcePath} 是主机上的目录绝对路径。该目录中的所有文件将以递归方式
 * 复制到工作区中的目标路径。
 * <p>
 * Layout entry that recursively copies a directory from the host filesystem into the sandbox
 * workspace.
 *
 * <p>The {@code sourcePath} is an absolute path to a directory on the host machine. All files
 * within that directory are copied recursively to the destination path in the workspace.
 */
public class LocalDirEntry extends WorkspaceEntry {

    private String sourcePath;

    /** 创建空本地目录条目。Creates an empty local directory entry. */
    public LocalDirEntry() {}

    /**
     * 创建包含主机源路径的本地目录条目。
     * <p>
     * Creates a local directory entry with the given host source path.
     *
     * @param sourcePath 主机文件系统上的目录绝对路径
     */
    public LocalDirEntry(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    /**
     * 返回主机端的源目录路径。
     * <p>
     * Returns the host-side source directory path.
     *
     * @return 主机目录绝对路径字符串
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * 设置主机端的源目录路径。
     * <p>
     * Sets the host-side source directory path.
     *
     * @param sourcePath 主机目录绝对路径字符串
     */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
}
