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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.sandbox.SandboxErrorCode;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 AgentScope MCP 客户端的 AgentRun 沙箱执行通道。
 * <p>
 * 复用 {@link McpClientBuilder#streamableHttpTransport(String)} 和 AgentRun API-Key 头，
 * 并公开 AgentRun 沙箱模板为 AgentScope 启用的三个工具名称：
 * {@code process_exec_cmd}、{@code read_file}、{@code write_file}。
 * <p>
 * Execution channel for an AgentRun sandbox built on the AgentScope MCP client.
 * Reuses {@link McpClientBuilder#streamableHttpTransport(String)} with the AgentRun API-key
 * header, and exposes the three tool names that an AgentRun sandbox template enables for
 * AgentScope: {@code process_exec_cmd}, {@code read_file}, {@code write_file}.
 */
final class AgentRunMcpChannel implements AutoCloseable {

    /** shell 风格命令执行的 MCP 工具名称。MCP tool name for shell-style command execution. */
    static final String TOOL_EXEC = "process_exec_cmd";

    /** 从沙箱文件系统读取文件的 MCP 工具名称。MCP tool name for reading a file from the sandbox filesystem. */
    static final String TOOL_READ_FILE = "read_file";

    /** 向沙箱文件系统写入文件的 MCP 工具名称。MCP tool name for writing a file to the sandbox filesystem. */
    static final String TOOL_WRITE_FILE = "write_file";

    /** Jackson JSON 对象映射器。Jackson JSON object mapper. */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** AgentRun 沙箱客户端选项。AgentRun sandbox client options. */
    private final AgentRunSandboxClientOptions opt;

    /** MCP 端点 URL。MCP endpoint URL. */
    private final String url;

    /** MCP 客户端包装器（volatile 保证可见性）。MCP client wrapper (volatile for visibility). */
    private volatile McpClientWrapper client;

    /**
     * 使用给定选项构造 AgentRunMcpChannel 实例。
     * Constructs an AgentRunMcpChannel instance with the given options.
     *
     * @param opt AgentRun 沙箱客户端选项 / AgentRun sandbox client options
     */
    AgentRunMcpChannel(AgentRunSandboxClientOptions opt) {
        this.opt = Objects.requireNonNull(opt, "opt");
        this.url = resolveUrl(opt);
    }

    /**
     * 连接 MCP 客户端。幂等操作——重复调用为无操作。
     * Connects the MCP client. Idempotent — repeated calls are a no-op.
     */
    void connect() {
        if (client != null) {
            return;
        }
        McpClientWrapper c =
                McpClientBuilder.create("agentrun-" + opt.getTemplateName())
                        .streamableHttpTransport(url)
                        .header("X-API-Key", requireApiKey())
                        .header("X-Acs-Parent-Id", nullToEmpty(opt.getAccountId()))
                        .timeout(Duration.ofSeconds(Math.max(60, opt.getReadTimeoutSeconds())))
                        .protocolVersions("2024-11-05", "2025-03-26")
                        .buildSync();
        c.initialize().block();
        this.client = c;
    }

    /**
     * 在沙箱中执行的 shell 命令结果。
     * Result of a shell command executed in the sandbox.
     */
    static final class ExecResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ExecResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
        }
    }

    /**
     * 通过 AgentRun {@code process_exec_cmd} MCP 工具运行 {@code command}。
     * Runs {@code command} via the AgentRun {@code process_exec_cmd} MCP tool.
     */
    ExecResult exec(String command, String cwd, int timeoutSeconds) {
        ensureConnected();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("command", command);
        if (cwd != null && !cwd.isBlank()) {
            args.put("cwd", cwd);
        }
        args.put("timeout", Math.max(1, timeoutSeconds));
        McpSchema.CallToolResult result =
                client.callTool(TOOL_EXEC, args)
                        .block(Duration.ofSeconds(Math.max(5, timeoutSeconds) + 30L));
        if (result == null) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.EXEC_TIMEOUT, "AgentRun MCP exec returned null");
        }
        if (Boolean.TRUE.equals(result.isError())) {
            String msg = extractText(result);
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_START_ERROR,
                    "AgentRun MCP " + TOOL_EXEC + " error: " + msg);
        }
        return parseExecPayload(extractText(result));
    }

    /**
     * 通过 AgentRun {@code read_file} MCP 工具读取文件，返回文本内容。
     * Reads a file via the AgentRun {@code read_file} MCP tool, returning its text content.
     */
    String readFile(String absolutePath) {
        ensureConnected();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", absolutePath);
        McpSchema.CallToolResult result =
                client.callTool(TOOL_READ_FILE, args).block(Duration.ofSeconds(120));
        if (result == null || Boolean.TRUE.equals(result.isError())) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_READ_ERROR,
                    "AgentRun MCP "
                            + TOOL_READ_FILE
                            + " failed for "
                            + absolutePath
                            + ": "
                            + (result == null ? "null" : extractText(result)));
        }
        return extractText(result);
    }

    /**
     * 通过 AgentRun {@code write_file} MCP 工具写入文件。
     * Writes a file via the AgentRun {@code write_file} MCP tool.
     */
    void writeFile(String absolutePath, String content) {
        ensureConnected();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", absolutePath);
        args.put("content", content != null ? content : "");
        McpSchema.CallToolResult result =
                client.callTool(TOOL_WRITE_FILE, args).block(Duration.ofSeconds(120));
        if (result == null || Boolean.TRUE.equals(result.isError())) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_WRITE_ERROR,
                    "AgentRun MCP "
                            + TOOL_WRITE_FILE
                            + " failed for "
                            + absolutePath
                            + ": "
                            + (result == null ? "null" : extractText(result)));
        }
    }

    /**
     * 返回此通道使用的 MCP 端点 URL。
     * Returns the MCP endpoint URL this channel uses.
     */
    String getUrl() {
        return url;
    }

    @Override
    public void close() {
        McpClientWrapper c = client;
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
                // 尽力而为 / best-effort
            }
            client = null;
        }
    }

    /**
     * 确保 MCP 客户端已连接。
     * Ensures the MCP client is connected.
     */
    private void ensureConnected() {
        if (client == null) {
            connect();
        }
    }

    /**
     * 获取 API 密钥，如果未设置则抛出异常。
     * Gets the API key or throws if not set.
     */
    private String requireApiKey() {
        String key = opt.getApiKey();
        if (key == null || key.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun API key is required (set AgentRunSandboxClientOptions#setApiKey)");
        }
        return key;
    }

    /**
     * 将 null 转换为空字符串。
     * Converts null to empty string.
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 解析 MCP 服务器 URL，如有必要则附加 MCP 端点路径。
     * Resolves the MCP server URL, appending the MCP endpoint path if needed.
     */
    private static String resolveUrl(AgentRunSandboxClientOptions opt) {
        String base = opt.getMcpServerUrl();
        if (base == null || base.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun MCP server URL is required (set #setMcpServerUrl)");
        }
        String endpoint = opt.getMcpEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return base;
        }
        // 将 base 视为主机根路径或已包含端点的情况。
        // Treat base as either a host root or already including the endpoint.
        if (base.endsWith(endpoint) || base.contains(endpoint + "?")) {
            return base;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String tail = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return trimmed + tail;
    }

    /**
     * 从 MCP 调用工具结果中提取文本内容。
     * Extracts text content from an MCP call tool result.
     */
    private static String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content c : result.content()) {
            if (c instanceof McpSchema.TextContent t && t.text() != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    /**
     * 解析 AgentRun exec MCP 响应。AgentRun 返回 JSON 对象
     * （如 {@code {"exitCode":0,"stdout":"...","stderr":"..."}}）或纯文本 stdout/stderr 字符串。
     * 两种格式均支持。
     * Parses an AgentRun exec MCP response. AgentRun returns either a JSON object such as
     * {@code {"exitCode":0,"stdout":"...","stderr":"..."}} or a plain stdout/stderr string. We
     * accept both shapes.
     */
    private static ExecResult parseExecPayload(String text) {
        if (text == null) {
            return new ExecResult(0, "", "");
        }
        String trimmed = text.strip();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = JSON.readTree(trimmed);
                int exit =
                        node.has("exitCode")
                                ? node.path("exitCode").asInt(0)
                                : node.path("exit_code").asInt(0);
                String stdout =
                        node.has("stdout")
                                ? node.path("stdout").asText("")
                                : node.path("output").asText("");
                String stderr = node.path("stderr").asText("");
                return new ExecResult(exit, stdout, stderr);
            } catch (Exception ignore) {
                // 回退到纯文本处理 / fall through to plain-text handling
            }
        }
        return new ExecResult(0, text, "");
    }
}
