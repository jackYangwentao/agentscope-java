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
package io.agentscope.harness.agent.sandbox.impl.daytona;

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
 * 用于 Daytona 控制平面和工具箱进程 API 的最小化 HTTP 客户端。
 * <p>
 * Minimal HTTP client for the Daytona control plane and toolbox process API.
 */
final class DaytonaHttp {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final DaytonaSandboxClientOptions opt;

    DaytonaHttp(DaytonaSandboxClientOptions opt) {
        this.opt = Objects.requireNonNull(opt, "opt");
        OkHttpClient base = opt.getHttpClient();
        if (base != null) {
            this.http = base;
        } else {
            this.http =
                    new OkHttpClient.Builder()
                            .connectTimeout(opt.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                            .readTimeout(opt.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                            .build();
        }
    }

    /**
     * 创建沙箱实例，返回沙箱 ID。
     * <p>
     * Create a sandbox instance and return the sandbox ID.
     */
    String createSandbox() throws IOException {
        ObjectNode body = json.createObjectNode();
        if (opt.getSnapshotId() != null && !opt.getSnapshotId().isBlank()) {
            body.put("snapshot", opt.getSnapshotId());
        } else {
            body.put("image", opt.getImage());
        }
        if (opt.getCpu() != null) {
            body.put("cpu", opt.getCpu());
        }
        if (opt.getMemory() != null) {
            body.put("memory", opt.getMemory());
        }
        if (opt.getDisk() != null) {
            body.put("disk", opt.getDisk());
        }
        JsonNode root =
                DaytonaRetry.withRetries(
                        opt.getMaxRetries(),
                        () -> postJson(opt.getControlPlaneBaseUrl() + "/sandbox", body));
        JsonNode id = root.get("id");
        if (id == null || id.asText().isBlank()) {
            id = root.get("sandboxId");
        }
        if (id == null || id.asText().isBlank()) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_START_ERROR,
                    "Daytona create sandbox response missing id: " + root);
        }
        return id.asText();
    }

    /**
     * 启动指定 ID 的沙箱实例。
     * <p>
     * Start the sandbox instance with the given ID.
     */
    void startSandbox(String sandboxId) throws IOException {
        String url = opt.getControlPlaneBaseUrl() + "/sandbox/" + sandboxId + "/start";
        DaytonaRetry.withRetries(opt.getMaxRetries(), () -> postJson(url, json.createObjectNode()));
    }

    /**
     * 获取指定沙箱的详细信息（JSON 格式）。
     * <p>
     * Get the details of the specified sandbox (JSON format).
     */
    JsonNode getSandbox(String sandboxId) throws IOException {
        String url = opt.getControlPlaneBaseUrl() + "/sandbox/" + sandboxId;
        return DaytonaRetry.withRetries(opt.getMaxRetries(), () -> getJson(url));
    }

    /**
     * 删除指定 ID 的沙箱实例。
     * <p>
     * Delete the sandbox instance with the given ID.
     */
    void deleteSandbox(String sandboxId) throws IOException {
        String url = opt.getControlPlaneBaseUrl() + "/sandbox/" + sandboxId;
        Request req =
                new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", bearer())
                        .delete()
                        .build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful() && res.code() != 404) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_START_ERROR,
                        "Daytona delete failed: HTTP " + res.code() + " " + res.message());
            }
        }
    }

    /**
     * 在沙箱中执行命令，返回执行结果 JSON。
     * <p>
     * Execute a command in the sandbox and return the execution result as JSON.
     */
    JsonNode execute(String sandboxId, String command, String cwd, int timeoutSeconds)
            throws IOException {
        ObjectNode body = json.createObjectNode();
        body.put("command", command);
        if (cwd != null && !cwd.isBlank()) {
            body.put("cwd", cwd);
        }
        body.put("timeout", Math.max(1, timeoutSeconds));
        String url = opt.getToolboxBaseUrl() + "/toolbox/" + sandboxId + "/process/execute";
        return DaytonaRetry.withRetries(
                opt.getMaxRetries(), () -> postJson(url, body, /* toolbox */ true));
    }

    /**
     * 轮询等待沙箱状态变为"已启动"。
     * <p>
     * Poll until the sandbox state transitions to "started".
     */
    void waitUntilStarted(String sandboxId, int maxWaitSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(maxWaitSeconds);
        while (System.nanoTime() < deadline) {
            JsonNode s = getSandbox(sandboxId);
            String st = textState(s);
            if (st != null && st.toLowerCase().contains("start")) {
                return;
            }
            Thread.sleep(1500L);
        }
        throw new SandboxException.SandboxRuntimeException(
                SandboxErrorCode.WORKSPACE_START_ERROR,
                "Daytona sandbox did not become ready in time: " + sandboxId);
    }

    /**
     * 从沙箱 JSON 响应中提取文本状态字段（优先 "state"，其次 "status"）。
     * <p>
     * Extract a text state field from the sandbox JSON response ("state" first, then "status").
     */
    private static String textState(JsonNode s) {
        if (s == null) {
            return null;
        }
        JsonNode st = s.get("state");
        if (st != null && st.isTextual()) {
            return st.asText();
        }
        JsonNode status = s.get("status");
        if (status != null && status.isTextual()) {
            return status.asText();
        }
        return null;
    }

    private JsonNode postJson(String url, ObjectNode body) throws IOException {
        return postJson(url, body, false);
    }

    /**
     * 发送 POST 请求并解析 JSON 响应。
     * <p>
     * Send a POST request and parse the JSON response.
     */
    private JsonNode postJson(String url, ObjectNode body, boolean toolbox) throws IOException {
        Request.Builder rb =
                new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body.toString(), JSON))
                        .addHeader("Authorization", bearer());
        if (toolbox) {
            rb.addHeader("Content-Type", "application/json");
        }
        try (Response res = http.newCall(rb.build()).execute()) {
            String text = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_START_ERROR,
                        "Daytona HTTP " + res.code() + " " + res.message() + ": " + text);
            }
            if (text.isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(text);
        }
    }

    /**
     * 发送 GET 请求并解析 JSON 响应。
     * <p>
     * Send a GET request and parse the JSON response.
     */
    private JsonNode getJson(String url) throws IOException {
        Request req =
                new Request.Builder().url(url).get().addHeader("Authorization", bearer()).build();
        try (Response res = http.newCall(req).execute()) {
            String text = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_START_ERROR,
                        "Daytona HTTP " + res.code() + " " + res.message() + ": " + text);
            }
            return json.readTree(text);
        }
    }

    /**
     * 构造 Bearer 认证头信息。
     * <p>
     * Build the Bearer authorization header.
     */
    private String bearer() {
        String key = opt.getApiKey();
        if (key == null || key.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "Daytona API key is required (set DaytonaSandboxClientOptions#setApiKey)");
        }
        return "Bearer " + key;
    }
}
