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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.harness.agent.sandbox.SandboxErrorCode;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * {@code https://api.e2b.app} 沙箱生命周期的 HTTP 客户端。
 * HTTP client for {@code https://api.e2b.app} sandbox lifecycle.
 */
final class E2bPlatformHttp {

    /** JSON 媒体类型常量。JSON media type constant. */
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** OkHttp HTTP 客户端。OkHttp HTTP client. */
    private final OkHttpClient http;

    /** Jackson JSON 对象映射器。Jackson JSON object mapper. */
    private final ObjectMapper json = new ObjectMapper();

    /** E2B 沙箱客户端选项。E2B sandbox client options. */
    private final E2bSandboxClientOptions opt;

    /**
     * 使用给定的选项构造 E2bPlatformHttp 实例。
     * Constructs an E2bPlatformHttp instance with the given options.
     *
     * @param opt E2B 沙箱客户端选项，不能为 null
     *            E2B sandbox client options, must not be null
     */
    E2bPlatformHttp(E2bSandboxClientOptions opt) {
        this.opt = Objects.requireNonNull(opt, "opt");
        if (opt.getHttpClient() != null) {
            this.http = opt.getHttpClient();
        } else {
            this.http =
                    new OkHttpClient.Builder()
                            .connectTimeout(opt.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                            .readTimeout(opt.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                            .build();
        }
    }

    /**
     * 向 E2B API 发送创建沙箱请求。
     * Sends a create sandbox request to the E2B API.
     *
     * @param templateId   模板 ID / template ID
     * @param timeoutSeconds 超时秒数 / timeout in seconds
     * @return 沙箱信息的 JSON 节点 / JSON node with sandbox info
     */
    JsonNode createSandbox(String templateId, int timeoutSeconds) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.put("templateID", templateId);
        body.put("timeout", timeoutSeconds);
        String url = trimSlash(opt.getApiBaseUrl()) + "/sandboxes";
        return E2bRetry.withRetries(
                opt.getMaxRetries(), () -> postJson(url, body, /* apiKey */ true));
    }

    /**
     * 向 E2B API 发送连接沙箱请求。
     * Sends a connect sandbox request to the E2B API.
     *
     * @param sandboxId     沙箱 ID / sandbox ID
     * @param timeoutSeconds 超时秒数 / timeout in seconds
     * @return 连接信息的 JSON 节点 / JSON node with connection info
     */
    JsonNode connectSandbox(String sandboxId, int timeoutSeconds) throws IOException {
        ObjectNode body = json.createObjectNode();
        body.put("timeout", timeoutSeconds);
        String url = trimSlash(opt.getApiBaseUrl()) + "/sandboxes/" + sandboxId + "/connect";
        return E2bRetry.withRetries(opt.getMaxRetries(), () -> postJson(url, body, true));
    }

    /**
     * 为指定沙箱创建快照。
     * Creates a snapshot for the specified sandbox.
     *
     * @param sandboxId 沙箱 ID / sandbox ID
     * @return 快照信息的 JSON 节点 / JSON node with snapshot info
     */
    JsonNode createSandboxSnapshot(String sandboxId) throws IOException {
        ObjectNode body = json.createObjectNode();
        String url = trimSlash(opt.getApiBaseUrl()) + "/sandboxes/" + sandboxId + "/snapshots";
        return E2bRetry.withRetries(opt.getMaxRetries(), () -> postJson(url, body, true));
    }

    /**
     * 向 E2B API 发送终止沙箱请求。
     * Sends a kill sandbox request to the E2B API.
     *
     * @param sandboxId 沙箱 ID / sandbox ID
     */
    void killSandbox(String sandboxId) throws IOException {
        String url = trimSlash(opt.getApiBaseUrl()) + "/sandboxes/" + sandboxId;
        Request req =
                new Request.Builder()
                        .url(url)
                        .addHeader("X-API-Key", requireApiKey())
                        .delete()
                        .build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful() && res.code() != 404) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_START_ERROR,
                        "E2B delete failed: HTTP " + res.code());
            }
        }
    }

    /**
     * 从 JSON 节点中提取字段并应用到沙箱状态对象。
     * Extracts fields from a JSON node and applies them to the sandbox state object.
     *
     * @param state 目标沙箱状态对象 / target sandbox state object
     * @param node  包含沙箱信息的 JSON 节点 / JSON node with sandbox info
     */
    void applySandboxFields(E2bSandboxState state, JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.hasNonNull("sandboxID")) {
            state.setSandboxId(node.get("sandboxID").asText());
        }
        if (node.hasNonNull("domain")) {
            state.setSandboxDomain(node.get("domain").asText());
        }
        if (node.hasNonNull("envdAccessToken")) {
            state.setEnvdAccessToken(node.get("envdAccessToken").asText());
        }
        if (node.hasNonNull("envdVersion")) {
            state.setEnvdVersion(node.get("envdVersion").asText());
        }
    }

    /**
     * 发送 POST JSON 请求到指定 URL。
     * Sends a POST JSON request to the specified URL.
     *
     * @param url    目标 URL / target URL
     * @param body   JSON 请求体 / JSON request body
     * @param apiKey 是否需要添加 API 密钥头 / whether to add the API key header
     * @return 响应的 JSON 节点 / response JSON node
     */
    private JsonNode postJson(String url, ObjectNode body, boolean apiKey) throws IOException {
        Request.Builder rb =
                new Request.Builder().url(url).post(RequestBody.create(body.toString(), JSON));
        if (apiKey) {
            rb.addHeader("X-API-Key", requireApiKey());
        }
        try (Response res = http.newCall(rb.build()).execute()) {
            String text = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_START_ERROR,
                        "E2B HTTP " + res.code() + ": " + text);
            }
            if (text.isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(text);
        }
    }

    /**
     * 获取 API 密钥，如果未设置则抛出异常。
     * Gets the API key or throws if not set.
     *
     * @return API 密钥字符串 / API key string
     */
    private String requireApiKey() {
        if (opt.getApiKey() == null || opt.getApiKey().isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "E2B API key is required (E2bSandboxClientOptions#setApiKey)");
        }
        return opt.getApiKey();
    }

    /**
     * 去除 URL 末尾的斜杠；如果 URL 为空则返回默认值。
     * Trims trailing slash from URL; returns default if URL is blank.
     *
     * @param u 输入 URL / input URL
     * @return 格式化后的 URL / formatted URL
     */
    private static String trimSlash(String u) {
        if (u == null || u.isBlank()) {
            return "https://api.e2b.app";
        }
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
