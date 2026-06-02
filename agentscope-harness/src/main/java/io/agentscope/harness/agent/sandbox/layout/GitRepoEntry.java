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
 * 将 Git 仓库克隆到沙箱工作区的布局条目。
 * <p>
 * 仓库将从给定的 {@code url} 和 {@code ref}（分支、标签或提交 SHA）克隆。
 * 此类用作 JSON 序列化的类型骨架。
 * <p>
 * Layout entry that clones a Git repository into the sandbox workspace.
 *
 * <p>The repository is cloned from {@code url} at the given {@code ref}
 * (branch, tag, or commit SHA).
 * This class serves as the type skeleton for JSON serialization.
 */
public class GitRepoEntry extends WorkspaceEntry {

    private String url;
    private String ref = "HEAD";

    /** 创建空的 git 仓库条目。Creates an empty git repo entry. */
    public GitRepoEntry() {}

    /**
     * 创建带 URL 和 ref 的 git 仓库条目。
     * <p>
     * Creates a git repo entry with the given URL and ref.
     *
     * @param url 仓库克隆 URL
     * @param ref 要检出的分支、标签或提交 SHA
     */
    public GitRepoEntry(String url, String ref) {
        this.url = url;
        this.ref = ref;
    }

    /**
     * 返回仓库克隆 URL。
     * <p>
     * Returns the repository clone URL.
     *
     * @return 克隆 URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置仓库克隆 URL。
     * <p>
     * Sets the repository clone URL.
     *
     * @param url 克隆 URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 返回要检出的 Git ref（分支、标签或提交 SHA）。
     * <p>
     * Returns the Git ref (branch, tag, or commit SHA) to check out.
     *
     * @return git ref
     */
    public String getRef() {
        return ref;
    }

    /**
     * 设置 Git ref。
     * <p>
     * Sets the Git ref.
     *
     * @param ref 分支、标签或提交 SHA
     */
    public void setRef(String ref) {
        this.ref = ref;
    }
}
