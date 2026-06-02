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
package io.agentscope.harness.agent.tools;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将 {@code workspace/tools.json} 中 {@code mcpServers} 下声明的 MCP 服务器注册到 {@link Toolkit}。
 * 每个条目根据其 {@code transport}（{@code stdio} / {@code sse} / {@code http}）
 * 通过 {@link McpClientBuilder} 构建为 {@link McpClientWrapper}，
 * 然后通过 {@link Toolkit#registration()} 注册，以便每台服务器的 {@code enableTools} 白名单生效。
 * Registers MCP servers declared under {@code mcpServers} in {@code workspace/tools.json} into a
 * {@link Toolkit}.
 *
 * <p>Each entry is built into an {@link McpClientWrapper} via {@link McpClientBuilder} according
 * to its {@code transport} ({@code stdio} / {@code sse} / {@code http}) and then registered through
 * {@link Toolkit#registration()} so that per-server {@code enableTools} allowlists are honoured.
 *
 * <p>单台服务器设置过程中的失败会被捕获并记录；其余服务器仍会注册，
 * 确保一个坏条目不会中止代理的启动流程。
 * Failures during a single server's setup are caught and logged; remaining servers still
 * register so that one bad entry never aborts the agent's bootstrap.
 */
public final class McpServerRegistrar {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistrar.class);

    private McpServerRegistrar() {}

    /**
     * 将 {@code servers} 中的每个条目注册到 {@code toolkit}。
     * 同步：每个服务器依次构建和注册后再开始下一个。
     * {@code servers} 可能为 {@code null} 或空（无操作）。
     */
    public static void register(Toolkit toolkit, Map<String, McpServerConfig> servers) {
        if (toolkit == null || servers == null || servers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
            String name = entry.getKey();
            McpServerConfig cfg = entry.getValue();
            if (name == null || name.isBlank() || cfg == null) {
                log.warn("Skipping MCP server with blank name or null config.");
                continue;
            }
            try {
                registerOne(toolkit, name, cfg);
            } catch (Exception e) {
                log.warn(
                        "Failed to register MCP server '{}' ({}): {}",
                        name,
                        cfg.getTransport(),
                        e.getMessage());
            }
        }
    }

    /** 注册单个 MCP 服务器。 */
    private static void registerOne(Toolkit toolkit, String name, McpServerConfig cfg) {
        McpClientWrapper wrapper = buildClient(name, cfg);
        Toolkit.ToolRegistration reg = toolkit.registration().mcpClient(wrapper);
        List<String> enableTools = cfg.getEnableTools();
        if (enableTools != null && !enableTools.isEmpty()) {
            reg.enableTools(enableTools);
        }
        reg.apply();
        log.info(
                "Registered MCP server '{}' (transport={}, enableTools={}).",
                name,
                cfg.getTransport(),
                enableTools);
    }

    /** 根据配置构建 MCP 客户端。 */
    private static McpClientWrapper buildClient(String name, McpServerConfig cfg) {
        String transport = cfg.getTransport();
        if (transport == null || transport.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP server '" + name + "' is missing required 'transport' field.");
        }
        McpClientBuilder builder = McpClientBuilder.create(name);
        switch (transport.toLowerCase(Locale.ROOT)) {
            case "stdio" -> configureStdio(builder, name, cfg);
            case "sse" -> configureSse(builder, name, cfg);
            case "http", "streamable-http", "streamablehttp" ->
                    configureStreamableHttp(builder, name, cfg);
            default ->
                    throw new IllegalArgumentException(
                            "MCP server '"
                                    + name
                                    + "' has unsupported transport '"
                                    + transport
                                    + "' (expected: stdio, sse, http).");
        }
        if (cfg.getTimeout() != null) {
            builder.timeout(cfg.getTimeout());
        }
        if (cfg.getInitializationTimeout() != null) {
            builder.initializationTimeout(cfg.getInitializationTimeout());
        }
        return builder.buildAsync().block();
    }

    /** 配置 stdio 传输类型的 MCP 客户端。 */
    private static void configureStdio(McpClientBuilder builder, String name, McpServerConfig cfg) {
        if (cfg.getCommand() == null || cfg.getCommand().isBlank()) {
            throw new IllegalArgumentException(
                    "stdio MCP server '" + name + "' requires a 'command'.");
        }
        List<String> args = cfg.getArgs() != null ? cfg.getArgs() : List.of();
        Map<String, String> env = cfg.getEnv() != null ? cfg.getEnv() : Map.of();
        builder.stdioTransport(cfg.getCommand(), args, env);
    }

    /** 配置 SSE 传输类型的 MCP 客户端。 */
    private static void configureSse(McpClientBuilder builder, String name, McpServerConfig cfg) {
        if (cfg.getUrl() == null || cfg.getUrl().isBlank()) {
            throw new IllegalArgumentException("sse MCP server '" + name + "' requires a 'url'.");
        }
        builder.sseTransport(cfg.getUrl());
        if (cfg.getHeaders() != null && !cfg.getHeaders().isEmpty()) {
            builder.headers(cfg.getHeaders());
        }
        if (cfg.getQueryParams() != null && !cfg.getQueryParams().isEmpty()) {
            builder.queryParams(cfg.getQueryParams());
        }
    }

    /** 配置流式 HTTP 传输类型的 MCP 客户端。 */
    private static void configureStreamableHttp(
            McpClientBuilder builder, String name, McpServerConfig cfg) {
        if (cfg.getUrl() == null || cfg.getUrl().isBlank()) {
            throw new IllegalArgumentException("http MCP server '" + name + "' requires a 'url'.");
        }
        builder.streamableHttpTransport(cfg.getUrl());
        if (cfg.getHeaders() != null && !cfg.getHeaders().isEmpty()) {
            builder.headers(cfg.getHeaders());
        }
        if (cfg.getQueryParams() != null && !cfg.getQueryParams().isEmpty()) {
            builder.queryParams(cfg.getQueryParams());
        }
    }
}
