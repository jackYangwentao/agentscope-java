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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创建一个目录的布局条目，可选包含嵌套的子条目。
 * <p>
 * 子条目是一个由文件名到 {@link WorkspaceEntry} 的映射，本身可以是嵌套的
 * {@code DirEntry} 实例，从而实现递归树状工作区结构。
 * <p>
 * Layout entry that creates a directory, optionally with nested child entries.
 *
 * <p>Child entries are a map of filename to {@link WorkspaceEntry} and may themselves be
 * nested {@code DirEntry} instances, enabling recursive tree-like workspace structures.
 */
public class DirEntry extends WorkspaceEntry {

    private Map<String, WorkspaceEntry> children = new LinkedHashMap<>();

    /** 创建空目录条目。Creates an empty directory entry. */
    public DirEntry() {}

    /**
     * 创建包含给定子条目的目录条目。
     * <p>
     * Creates a directory entry with the given children.
     *
     * @param children 子条目名称到子条目的映射
     */
    public DirEntry(Map<String, WorkspaceEntry> children) {
        this.children = new LinkedHashMap<>(children);
    }

    /**
     * 返回此目录中的子条目。
     * <p>
     * Returns the child entries in this directory.
     *
     * @return 子条目名称到子条目的可变映射
     */
    public Map<String, WorkspaceEntry> getChildren() {
        return children;
    }

    /**
     * 设置此目录的子条目。
     * <p>
     * Sets the child entries for this directory.
     *
     * @param children 子条目名称到子条目的映射
     */
    public void setChildren(Map<String, WorkspaceEntry> children) {
        this.children = children != null ? children : new LinkedHashMap<>();
    }

    /**
     * 添加子条目到此目录。
     * <p>
     * Adds a child entry to this directory.
     *
     * @param name  子条目标题
     * @param entry 子条目
     * @return 此实例（支持链式调用）
     */
    public DirEntry child(String name, WorkspaceEntry entry) {
        this.children.put(name, entry);
        return this;
    }
}
