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
 * 从主机文件系统复制单个文件到沙箱工作区的布局条目。
 * <p>
 * {@code sourcePath} 是主机上的绝对路径。它以字符串形式存储，
 * 以确保跨平台的 JSON 序列化安全。
 * <p>
 * Layout entry that copies a single file from the host filesystem into the sandbox workspace.
 *
 * <p>The {@code sourcePath} is an absolute path on the host machine. It is stored as a string
 * to ensure safe JSON serialization across platforms.
 */
public class LocalFileEntry extends WorkspaceEntry {

    private String sourcePath;

    /** 创建空本地文件条目。Creates an empty local file entry. */
    public LocalFileEntry() {}

    /**
     * 创建包含主机源路径的本地文件条目。
     * <p>
     * Creates a local file entry with the given host source path.
     *
     * @param sourcePath 主机文件系统上的绝对路径
     */
    public LocalFileEntry(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    /**
     * 返回主机端的源路径。
     * <p>
     * Returns the host-side source path.
     *
     * @return 主机绝对路径字符串
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * 设置主机端的源路径。
     * <p>
     * Sets the host-side source path.
     *
     * @param sourcePath 主机绝对路径字符串
     */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
}
