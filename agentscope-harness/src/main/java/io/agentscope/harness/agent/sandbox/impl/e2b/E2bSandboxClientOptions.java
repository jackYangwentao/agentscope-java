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
package io.agentscope.harness.agent.sandbox.impl.e2b;

import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import okhttp3.OkHttpClient;

/**
 * {@link E2bSandboxClient} 的选项配置。
 * Options for {@link E2bSandboxClient}.
 */
public class E2bSandboxClientOptions extends SandboxClientOptions {

    /** 自定义 OkHttp 客户端。Custom OkHttp client. */
    private OkHttpClient httpClient;

    /** E2B API 密钥。E2B API key. */
    private String apiKey;

    /** E2B API 基础 URL。默认值 {@code "https://api.e2b.app"}。E2B API base URL. */
    private String apiBaseUrl = "https://api.e2b.app";

    /** E2B 域。默认值 {@code "e2b.app"}。E2B domain. */
    private String domain = "e2b.app";

    /** E2B 模板 ID（从快照创建时可用作快照 ID）。默认值 {@code "base"}。E2B template id (or snapshot id when creating from a snapshot). */
    private String templateId = "base";

    /** 沙箱内工作空间的绝对路径。默认值 {@code "/home/user"}。Absolute path of the workspace root inside the sandbox. */
    private String workspaceRoot = "/home/user";

    /** 沙箱超时秒数。默认值 {@code 300}。Sandbox timeout in seconds. */
    private int sandboxTimeoutSeconds = 300;

    /** 运行用户。默认值 {@code "user"}。Run user. */
    private String runUser = "user";

    /** 持久化模式。默认值 {@link E2bPersistenceMode#TAR}。Persistence mode. */
    private E2bPersistenceMode persistenceMode = E2bPersistenceMode.TAR;

    /** 连接超时秒数。默认值 {@code 30}。Connect timeout in seconds. */
    private int connectTimeoutSeconds = 30;

    /** 读取超时秒数。默认值 {@code 120}。Read timeout in seconds. */
    private int readTimeoutSeconds = 120;

    /** 最大重试次数。默认值 {@code 3}。Maximum retry count. */
    private int maxRetries = 3;

    @Override
    public String getType() {
        return "e2b";
    }

    @Override
    public SandboxClient<? extends SandboxClientOptions> createClient() {
        return new E2bSandboxClient(this, null);
    }

    /**
     * 返回自定义 OkHttp 客户端。
     * Returns the custom OkHttp client.
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 设置自定义 OkHttp 客户端。
     * Sets the custom OkHttp client.
     */
    public void setHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 返回 E2B API 密钥。
     * Returns the E2B API key.
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置 E2B API 密钥。
     * Sets the E2B API key.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 返回 E2B API 基础 URL。
     * Returns the E2B API base URL.
     */
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * 设置 E2B API 基础 URL。
     * Sets the E2B API base URL.
     */
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * 返回 E2B 域。
     * Returns the E2B domain.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * 设置 E2B 域。
     * Sets the E2B domain.
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * 返回模板 ID。
     * Returns the template ID.
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * 设置模板 ID。
     * Sets the template ID.
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * 返回工作空间根路径。
     * Returns the workspace root path.
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 设置工作空间根路径。
     * Sets the workspace root path.
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * 返回沙箱超时秒数。
     * Returns the sandbox timeout in seconds.
     */
    public int getSandboxTimeoutSeconds() {
        return sandboxTimeoutSeconds;
    }

    /**
     * 设置沙箱超时秒数。
     * Sets the sandbox timeout in seconds.
     */
    public void setSandboxTimeoutSeconds(int sandboxTimeoutSeconds) {
        this.sandboxTimeoutSeconds = sandboxTimeoutSeconds;
    }

    /**
     * 返回运行用户。
     * Returns the run user.
     */
    public String getRunUser() {
        return runUser;
    }

    /**
     * 设置运行用户。
     * Sets the run user.
     */
    public void setRunUser(String runUser) {
        this.runUser = runUser;
    }

    /**
     * 返回持久化模式。
     * Returns the persistence mode.
     */
    public E2bPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    /**
     * 设置持久化模式。
     * Sets the persistence mode.
     */
    public void setPersistenceMode(E2bPersistenceMode persistenceMode) {
        this.persistenceMode = persistenceMode != null ? persistenceMode : E2bPersistenceMode.TAR;
    }

    /**
     * 返回连接超时秒数。
     * Returns the connect timeout in seconds.
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * 设置连接超时秒数。
     * Sets the connect timeout in seconds.
     */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /**
     * 返回读取超时秒数。
     * Returns the read timeout in seconds.
     */
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    /**
     * 设置读取超时秒数。
     * Sets the read timeout in seconds.
     */
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    /**
     * 返回最大重试次数。
     * Returns the maximum retry count.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 设置最大重试次数。
     * Sets the maximum retry count.
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
