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
package io.agentscope.harness.agent.sandbox.impl.agentrun;

import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;

/**
 * {@link AgentRunSandboxClient} 的选项配置。
 * Options for {@link AgentRunSandboxClient}.
 */
public class AgentRunSandboxClientOptions extends SandboxClientOptions {

    /** AgentRun 每沙箱接受的最大实例级 OSS 挂载数。Maximum number of instance-level OSS mounts AgentRun accepts per sandbox. */
    public static final int MAX_OSS_MOUNTS = 5;

    /** NAS 和 OSS 实例挂载的允许挂载目录前缀。Allowed mount-dir prefixes for both NAS and OSS instance mounts. */
    public static final List<String> ALLOWED_MOUNT_PREFIXES = List.of("/home/", "/mnt/", "/data/");

    /** 自定义 OkHttp 客户端。Custom OkHttp client. */
    private OkHttpClient httpClient;

    /** API 密钥。API key. */
    private String apiKey;

    /** 账户 ID。Account ID. */
    private String accountId;

    /** 区域。Region. */
    private String region;

    /** 数据面基础 URL。Data plane base URL. */
    private String dataPlaneBaseUrl;

    /** 模板名称。Template name. */
    private String templateName;

    /** MCP 服务器 URL。MCP server URL. */
    private String mcpServerUrl;

    /** MCP 端点路径。默认值 {@code "/mcp"}。MCP endpoint path. */
    private String mcpEndpoint = "/mcp";

    /** 沙箱空闲超时秒数。默认值 {@code 1800}。Sandbox idle timeout in seconds. */
    private int sandboxIdleTimeoutSeconds = 1800;

    /** NAS 挂载配置。NAS mount config. */
    private AgentRunNasMountConfig nasConfig;

    /** OSS 挂载配置列表。List of OSS mount configs. */
    private List<AgentRunOssMountConfig> ossMountConfigs = new ArrayList<>();

    /** 工作空间根路径。默认值 {@link AgentRunSandboxState#DEFAULT_WORKSPACE_ROOT}。Workspace root path. */
    private String workspaceRoot = AgentRunSandboxState.DEFAULT_WORKSPACE_ROOT;

    /** 连接超时秒数。默认值 {@code 30}。Connect timeout in seconds. */
    private int connectTimeoutSeconds = 30;

    /** 读取超时秒数。默认值 {@code 120}。Read timeout in seconds. */
    private int readTimeoutSeconds = 120;

    /** 最大重试次数。默认值 {@code 3}。Maximum retry count. */
    private int maxRetries = 3;

    @Override
    public String getType() {
        return "agentrun";
    }

    @Override
    public SandboxClient<? extends SandboxClientOptions> createClient() {
        return new AgentRunSandboxClient(this, null);
    }

    /**
     * 验证与任何活跃沙箱无关的选项不变量。
     * Validates option invariants that are independent of any active sandbox.
     *
     * @throws SandboxException.SandboxConfigurationException 违反约束时抛出 / when a constraint is violated
     */
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun API key is required (set AgentRunSandboxClientOptions#setApiKey)");
        }
        if (templateName == null || templateName.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun template name is required (set #setTemplateName)");
        }
        if (mcpServerUrl == null || mcpServerUrl.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun MCP server URL is required (set #setMcpServerUrl)");
        }
        if (nasConfig != null && nasConfig.getMountDir() != null) {
            requireAllowedMountDir("nasConfig.mountDir", nasConfig.getMountDir());
        }
        if (ossMountConfigs != null) {
            if (ossMountConfigs.size() > MAX_OSS_MOUNTS) {
                throw new SandboxException.SandboxConfigurationException(
                        "AgentRun supports at most "
                                + MAX_OSS_MOUNTS
                                + " OSS mounts per sandbox; got "
                                + ossMountConfigs.size());
            }
            for (int i = 0; i < ossMountConfigs.size(); i++) {
                AgentRunOssMountConfig m = ossMountConfigs.get(i);
                if (m == null) {
                    continue;
                }
                requireAllowedMountDir("ossMountConfigs[" + i + "].mountDir", m.getMountDir());
            }
        }
    }

    /**
     * 验证挂载目录是否以允许的前缀开头。
     * Validates that the mount directory starts with an allowed prefix.
     */
    private static void requireAllowedMountDir(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    field + " must be set (under /home, /mnt or /data)");
        }
        for (String prefix : ALLOWED_MOUNT_PREFIXES) {
            if (value.startsWith(prefix)) {
                return;
            }
        }
        throw new SandboxException.SandboxConfigurationException(
                field + " must start with one of " + ALLOWED_MOUNT_PREFIXES + " but was: " + value);
    }

    /**
     * 返回解析后的数据面基础 URL，如果未显式设置则从 {@code accountId}/{@code region} 推导。
     * Returns the resolved data-plane base URL, deriving from {@code accountId}/{@code region}
     * when not explicitly set.
     *
     * @return 绝对数据面基础 URL / absolute data-plane base URL
     */
    public String getResolvedDataPlaneBaseUrl() {
        if (dataPlaneBaseUrl != null && !dataPlaneBaseUrl.isBlank()) {
            return stripTrailingSlash(dataPlaneBaseUrl);
        }
        if (accountId == null || accountId.isBlank() || region == null || region.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun requires accountId+region or an explicit dataPlaneBaseUrl");
        }
        return "https://" + accountId + ".agentrun-data." + region + ".aliyuncs.com";
    }

    /**
     * 去除 URL 末尾的斜杠。
     * Strips trailing slash from URL.
     */
    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** 返回自定义 OkHttp 客户端。Returns the custom OkHttp client. */
    public OkHttpClient getHttpClient() { return httpClient; }

    /** 设置自定义 OkHttp 客户端并返回当前实例（流式 API）。*/
    public AgentRunSandboxClientOptions setHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    /** 返回 API 密钥。Returns the API key. */
    public String getApiKey() { return apiKey; }

    /** 设置 API 密钥并返回当前实例。*/
    public AgentRunSandboxClientOptions setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /** 返回账户 ID。Returns the account ID. */
    public String getAccountId() { return accountId; }

    /** 设置账户 ID 并返回当前实例。*/
    public AgentRunSandboxClientOptions setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    /** 返回区域。Returns the region. */
    public String getRegion() { return region; }

    /** 设置区域并返回当前实例。*/
    public AgentRunSandboxClientOptions setRegion(String region) {
        this.region = region;
        return this;
    }

    /** 返回数据面基础 URL。Returns the data plane base URL. */
    public String getDataPlaneBaseUrl() { return dataPlaneBaseUrl; }

    /** 设置数据面基础 URL 并返回当前实例。*/
    public AgentRunSandboxClientOptions setDataPlaneBaseUrl(String dataPlaneBaseUrl) {
        this.dataPlaneBaseUrl = dataPlaneBaseUrl;
        return this;
    }

    /** 返回模板名称。Returns the template name. */
    public String getTemplateName() { return templateName; }

    /** 设置模板名称并返回当前实例。*/
    public AgentRunSandboxClientOptions setTemplateName(String templateName) {
        this.templateName = templateName;
        return this;
    }

    /** 返回 MCP 服务器 URL。Returns the MCP server URL. */
    public String getMcpServerUrl() { return mcpServerUrl; }

    /** 设置 MCP 服务器 URL 并返回当前实例。*/
    public AgentRunSandboxClientOptions setMcpServerUrl(String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
        return this;
    }

    /** 返回 MCP 端点路径。Returns the MCP endpoint path. */
    public String getMcpEndpoint() { return mcpEndpoint; }

    /** 设置 MCP 端点路径并返回当前实例。*/
    public AgentRunSandboxClientOptions setMcpEndpoint(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint != null && !mcpEndpoint.isBlank() ? mcpEndpoint : "/mcp";
        return this;
    }

    /** 返回沙箱空闲超时秒数。Returns the sandbox idle timeout in seconds. */
    public int getSandboxIdleTimeoutSeconds() { return sandboxIdleTimeoutSeconds; }

    /** 设置沙箱空闲超时秒数并返回当前实例。*/
    public AgentRunSandboxClientOptions setSandboxIdleTimeoutSeconds(int seconds) {
        this.sandboxIdleTimeoutSeconds = seconds;
        return this;
    }

    /** 返回 NAS 挂载配置。Returns the NAS mount config. */
    public AgentRunNasMountConfig getNasConfig() { return nasConfig; }

    /** 设置 NAS 挂载配置并返回当前实例。*/
    public AgentRunSandboxClientOptions setNasConfig(AgentRunNasMountConfig nasConfig) {
        this.nasConfig = nasConfig;
        return this;
    }

    /** 返回 OSS 挂载配置列表。Returns the list of OSS mount configs. */
    public List<AgentRunOssMountConfig> getOssMountConfigs() { return ossMountConfigs; }

    /** 设置 OSS 挂载配置列表并返回当前实例。*/
    public AgentRunSandboxClientOptions setOssMountConfigs(
            List<AgentRunOssMountConfig> ossMountConfigs) {
        this.ossMountConfigs = ossMountConfigs != null ? ossMountConfigs : new ArrayList<>();
        return this;
    }

    /** 添加 OSS 挂载配置并返回当前实例。*/
    public AgentRunSandboxClientOptions addOssMount(AgentRunOssMountConfig mount) {
        if (mount != null) {
            this.ossMountConfigs.add(mount);
        }
        return this;
    }

    /** 返回工作空间根路径。Returns the workspace root path. */
    public String getWorkspaceRoot() { return workspaceRoot; }

    /** 设置工作空间根路径并返回当前实例。*/
    public AgentRunSandboxClientOptions setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot =
                workspaceRoot != null ? workspaceRoot : AgentRunSandboxState.DEFAULT_WORKSPACE_ROOT;
        return this;
    }

    /** 返回连接超时秒数。Returns the connect timeout in seconds. */
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }

    /** 设置连接超时秒数并返回当前实例。*/
    public AgentRunSandboxClientOptions setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        return this;
    }

    /** 返回读取超时秒数。Returns the read timeout in seconds. */
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }

    /** 设置读取超时秒数并返回当前实例。*/
    public AgentRunSandboxClientOptions setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
        return this;
    }

    /** 返回最大重试次数。Returns the maximum retry count. */
    public int getMaxRetries() { return maxRetries; }

    /** 设置最大重试次数并返回当前实例。*/
    public AgentRunSandboxClientOptions setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }
}
