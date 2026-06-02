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
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.AbstractBaseSandbox;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.SandboxErrorCode;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.WorkspaceMountSupport;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Daytona 云沙箱的 {@link io.agentscope.harness.agent.sandbox.Sandbox} 实现。
 * <p>
 * {@link io.agentscope.harness.agent.sandbox.Sandbox} backed by Daytona cloud sandboxes.
 */
public class DaytonaSandbox extends AbstractBaseSandbox {

    private static final Logger log = LoggerFactory.getLogger(DaytonaSandbox.class);

    /** 输出截断阈值：512 KB */
    private static final int OUTPUT_TRUNCATE_BYTES = 512 * 1024;
    /** tar 操作的超时时间：300 秒 */
    private static final int TAR_TIMEOUT_SECONDS = 300;
    /** Base64 编码的分块大小：4000 字符 */
    private static final int B64_CHUNK = 4000;

    private final DaytonaSandboxState daytonaState;
    private final DaytonaHttp http;

    public DaytonaSandbox(DaytonaSandboxState state, DaytonaHttp http) {
        super(state);
        this.daytonaState = state;
        this.http = http;
    }

    /**
     * 启动沙箱，检查绑定挂载并确保沙箱实例就绪。
     * <p>
     * Start the sandbox, checking bind mounts and ensuring the sandbox instance is ready.
     */
    @Override
    public void start() throws Exception {
        if (WorkspaceMountSupport.hasBindMounts(daytonaState.getWorkspaceSpec())) {
            log.warn(
                    "[sandbox-daytona] WorkspaceSpec contains bind_mount entries; "
                            + "Daytona does not apply host bind mounts — paths are not mounted.");
        }
        ensureSandbox();
        super.start();
    }

    /**
     * 关闭沙箱，释放受管的沙箱资源。
     * <p>
     * Shutdown the sandbox, releasing owned sandbox resources.
     */
    @Override
    public void shutdown() throws Exception {
        if (!daytonaState.isSandboxOwned()) {
            return;
        }
        String id = daytonaState.getSandboxId();
        if (id != null && !id.isBlank()) {
            http.deleteSandbox(id);
        }
    }

    /**
     * 在沙箱中执行命令并返回执行结果。
     * <p>
     * Execute a command in the sandbox and return the execution result.
     */
    @Override
    protected ExecResult doExec(RuntimeContext runtimeContext, String command, int timeoutSeconds)
            throws Exception {
        JsonNode j =
                http.execute(
                        daytonaState.getSandboxId(),
                        command,
                        relativeCwd(daytonaState.getWorkspaceRoot()),
                        timeoutSeconds);
        int exit = j.path("exitCode").asInt(-1);
        String out = j.path("result").asText("");
        boolean truncated = out.length() >= OUTPUT_TRUNCATE_BYTES;
        if (truncated) {
            out = out.substring(0, OUTPUT_TRUNCATE_BYTES);
        }
        ExecResult r = new ExecResult(exit, out, "", truncated);
        if (!r.ok()) {
            throw new SandboxException.ExecException(exit, out, "");
        }
        return r;
    }

    /**
     * 将工作区打包为 tar 归档流（通过 base64 编码通道）。
     * <p>
     * Package the workspace into a tar archive stream (via base64 encoding channel).
     */
    @Override
    protected InputStream doPersistWorkspace() throws Exception {
        String root = daytonaState.getWorkspaceRoot();
        String cmd = "tar -cf - -C " + shellSingleQuote(root) + " . | base64 -w0";
        JsonNode j =
                http.execute(
                        daytonaState.getSandboxId(), cmd, relativeCwd(root), TAR_TIMEOUT_SECONDS);
        int exit = j.path("exitCode").asInt(-1);
        if (exit != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_WRITE_ERROR,
                    "Daytona tar failed (exit=" + exit + "): " + j.path("result").asText(""));
        }
        String b64 = j.path("result").asText("").replace("\n", "").replace("\r", "");
        byte[] raw = Base64.getDecoder().decode(b64);
        return new ByteArrayInputStream(raw);
    }

    /**
     * 将 tar 归档流恢复到工作区（通过 base64 分块写入 + Python 辅助）。
     * <p>
     * Restore a tar archive stream to the workspace (via base64 chunked write + Python helpers).
     */
    @Override
    protected void doHydrateWorkspace(InputStream archive) throws Exception {
        String root = daytonaState.getWorkspaceRoot();
        String rel = relativeCwd(root);
        byte[] all = archive.readAllBytes();
        String b64 = Base64.getEncoder().encodeToString(all);
        http.execute(daytonaState.getSandboxId(), "rm -f /tmp/agentscope-ws.b64", rel, 30);
        ObjectMapper om = new ObjectMapper();
        for (int i = 0; i < b64.length(); i += B64_CHUNK) {
            String chunk = b64.substring(i, Math.min(b64.length(), i + B64_CHUNK));
            String lit = om.writeValueAsString(chunk);
            String py =
                    "import pathlib; pathlib.Path('/tmp/agentscope-ws.b64').open('a').write("
                            + lit
                            + ")";
            JsonNode j =
                    http.execute(
                            daytonaState.getSandboxId(),
                            "python3 -c " + shellSingleQuote(py),
                            rel,
                            120);
            if (j.path("exitCode").asInt(-1) != 0) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_ARCHIVE_READ_ERROR,
                        "Daytona chunk write failed: " + j.path("result").asText(""));
            }
        }
        String pyFin =
                "import base64,pathlib,subprocess; d="
                        + om.writeValueAsString(root)
                        + "; raw=base64.standard_b64decode(pathlib.Path('/tmp/agentscope-ws.b64').read_text());"
                        + " subprocess.run(['tar','xf','-','-C',d],input=raw,check=True)";
        JsonNode fin =
                http.execute(
                        daytonaState.getSandboxId(),
                        "python3 -c " + shellSingleQuote(pyFin),
                        rel,
                        TAR_TIMEOUT_SECONDS);
        if (fin.path("exitCode").asInt(-1) != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_READ_ERROR,
                    "Daytona tar extract failed: " + fin.path("result").asText(""));
        }
    }

    /**
     * 创建工作区根目录。
     * <p>
     * Create the workspace root directory.
     */
    @Override
    protected void doSetupWorkspace() throws Exception {
        exec(null, "mkdir -p " + shellSingleQuote(daytonaState.getWorkspaceRoot()), 30);
    }

    /**
     * 销毁工作区，清理文件（尽力而为）。
     * <p>
     * Destroy the workspace, cleaning up files (best-effort).
     */
    @Override
    protected void doDestroyWorkspace() throws Exception {
        try {
            exec(null, "rm -rf " + shellSingleQuote(daytonaState.getWorkspaceRoot()), 30);
        } catch (Exception e) {
            // best-effort
        }
    }

    /**
     * 获取工作区根路径。
     * <p>
     * Get the workspace root path.
     */
    @Override
    protected String getWorkspaceRoot() {
        return daytonaState.getWorkspaceRoot();
    }

    /**
     * 确保沙箱实例已创建并处于运行状态；如果现有实例不可用则重建。
     * <p>
     * Ensure the sandbox instance is created and running; rebuild if the existing instance is
     * unavailable.
     */
    private void ensureSandbox() throws Exception {
        if (daytonaState.getSandboxId() == null || daytonaState.getSandboxId().isBlank()) {
            String id = http.createSandbox();
            daytonaState.setSandboxId(id);
            http.startSandbox(id);
            http.waitUntilStarted(id, 300);
            return;
        }
        try {
            http.getSandbox(daytonaState.getSandboxId());
        } catch (Exception e) {
            daytonaState.setWorkspaceRootReady(false);
            String id = http.createSandbox();
            daytonaState.setSandboxId(id);
            http.startSandbox(id);
            http.waitUntilStarted(id, 300);
            return;
        }
        http.startSandbox(daytonaState.getSandboxId());
        http.waitUntilStarted(daytonaState.getSandboxId(), 120);
    }

    /**
     * 将绝对路径转为相对路径（去除开头的 "/"）。
     * <p>
     * Convert an absolute path to a relative path (strip leading "/").
     */
    private static String relativeCwd(String abs) {
        if (abs == null || abs.isBlank()) {
            return "";
        }
        return abs.startsWith("/") ? abs.substring(1) : abs;
    }

    /**
     * 使用 shell 单引号转义字符串。
     * <p>
     * Escape a string with shell single quotes.
     */
    private static String shellSingleQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
