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
 * 由阿里云 AgentRun 沙箱（FC 3.0 Sandbox API 2025-09-10）驱动的
 * {@link io.agentscope.harness.agent.sandbox.Sandbox} 实现。
 * <p>
 * 执行基于与沙箱模板捆绑的 AgentRun MCP 服务器运行。
 * 当沙箱状态声明 {@code workspaceOnNas=true} 时，持久化完全委托给 NAS 挂载，
 * {@link #doPersistWorkspace()} 为无操作；否则沙箱回退到与 Daytona 负载形状相同的
 * 基于 MCP 的 tar 归档。
 * <p>
 * {@link io.agentscope.harness.agent.sandbox.Sandbox} backed by an Alibaba Cloud AgentRun sandbox
 * (FC 3.0 Sandbox API 2025-09-10).
 * Execution runs over the AgentRun MCP server bundled with the sandbox template. When the
 * sandbox state declares {@code workspaceOnNas=true}, persistence is delegated entirely to the
 * NAS mount and {@link #doPersistWorkspace()} is a no-op; otherwise the sandbox falls back to a
 * tar-via-MCP archive identical in shape to Daytona's payload.
 */
public class AgentRunSandbox extends AbstractBaseSandbox {

    /** 日志记录器。Logger. */
    private static final Logger log = LoggerFactory.getLogger(AgentRunSandbox.class);

    /** 输出截断字节数上限。Output truncation byte limit. */
    private static final int OUTPUT_TRUNCATE_BYTES = 512 * 1024;

    /** tar 操作的超时秒数。Tar operation timeout in seconds. */
    private static final int TAR_TIMEOUT_SECONDS = 300;

    /** 等待沙箱启动的秒数。Start wait seconds. */
    private static final int START_WAIT_SECONDS = 300;

    /** Base64 分块大小。Base64 chunk size. */
    private static final int B64_CHUNK = 4000;

    /** AgentRun 沙箱状态。AgentRun sandbox state. */
    private final AgentRunSandboxState arState;

    /** AgentRun 沙箱客户端选项。AgentRun sandbox client options. */
    private final AgentRunSandboxClientOptions options;

    /** AgentRun 数据面 HTTP 客户端。AgentRun data plane HTTP client. */
    private final AgentRunDataPlaneHttp http;

    /** AgentRun MCP 通道。AgentRun MCP channel. */
    private final AgentRunMcpChannel mcp;

    /**
     * 使用给定状态、选项、HTTP 客户端和 MCP 通道构造 AgentRunSandbox 实例。
     * Constructs an AgentRunSandbox instance with the given state, options, HTTP client and MCP channel.
     */
    public AgentRunSandbox(
            AgentRunSandboxState state,
            AgentRunSandboxClientOptions options,
            AgentRunDataPlaneHttp http,
            AgentRunMcpChannel mcp) {
        super(state);
        this.arState = state;
        this.options = options;
        this.http = http;
        this.mcp = mcp;
    }

    /**
     * 启动沙箱，确保沙箱存在并连接 MCP 通道。
     * Starts the sandbox, ensuring sandbox existence and MCP channel connection.
     */
    @Override
    public void start() throws Exception {
        if (WorkspaceMountSupport.hasBindMounts(arState.getWorkspaceSpec())) {
            log.warn(
                    "[sandbox-agentrun] WorkspaceSpec contains bind_mount entries; "
                            + "AgentRun does not apply host bind mounts — paths are not mounted.");
        }
        ensureSandbox();
        mcp.connect();
        super.start();
    }

    /**
     * 停止沙箱。如果工作空间位于 NAS 上，先执行 sync 再停止。
     * Stops the sandbox. Syncs first if workspace is on NAS.
     */
    @Override
    public void stop() throws Exception {
        if (arState.isWorkspaceOnNas()) {
            try {
                mcp.exec("sync", null, 5);
            } catch (Exception e) {
                log.warn("[sandbox-agentrun] sync before stop failed: {}", e.getMessage());
            }
        }
        super.stop();
    }

    /**
     * 关闭沙箱，关闭 MCP 通道并删除沙箱后端资源。
     * Shuts down the sandbox, closing MCP channel and deleting backend resources.
     */
    @Override
    public void shutdown() throws Exception {
        try {
            mcp.close();
        } catch (Exception ignore) {
            // 尽力而为 / best-effort
        }
        if (!arState.isSandboxOwned()) {
            return;
        }
        String id = arState.getSandboxId();
        if (id != null && !id.isBlank()) {
            http.deleteSandbox(id);
        }
    }

    /**
     * 探测工作空间根目录是否因持久化挂载而保留。
     * Probes whether workspace root is preserved due to persistent mounts.
     */
    @Override
    protected boolean probeWorkspaceRootForPreservedResume() {
        if (arState.isWorkspaceOnNas()) {
            // 文件位于持久化挂载上——目录在沙箱重建后仍然存在，因此视为保留。
            // Files live on a persistent mount — the directory is durable across sandbox
            // recreations, so we treat it as preserved without probing.
            return true;
        }
        return super.probeWorkspaceRootForPreservedResume();
    }

    @Override
    protected ExecResult doExec(RuntimeContext runtimeContext, String command, int timeoutSeconds)
            throws Exception {
        AgentRunMcpChannel.ExecResult r =
                mcp.exec(command, relativeOrAbsoluteCwd(), timeoutSeconds);
        String out = r.stdout != null ? r.stdout : "";
        boolean truncated = out.length() >= OUTPUT_TRUNCATE_BYTES;
        if (truncated) {
            out = out.substring(0, OUTPUT_TRUNCATE_BYTES);
        }
        ExecResult result = new ExecResult(r.exitCode, out, r.stderr, truncated);
        if (!result.ok()) {
            throw new SandboxException.ExecException(r.exitCode, out, r.stderr);
        }
        return result;
    }

    /**
     * 持久化工作空间。NAS 模式下直接跳过；否则通过 MCP 执行 tar + base64 归档。
     * Persists workspace. Skips on NAS; otherwise tar + base64 via MCP.
     */
    @Override
    protected InputStream doPersistWorkspace() throws Exception {
        if (arState.isWorkspaceOnNas()) {
            // 持久化由 NAS/OSS 挂载处理——无需归档。
            // Persistence is handled by the NAS/OSS mount — nothing to archive.
            return InputStream.nullInputStream();
        }
        String root = arState.getWorkspaceRoot();
        String cmd = "tar -cf - -C " + shellSingleQuote(root) + " . | base64 -w0";
        AgentRunMcpChannel.ExecResult r = mcp.exec(cmd, null, TAR_TIMEOUT_SECONDS);
        if (r.exitCode != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_WRITE_ERROR,
                    "AgentRun tar failed (exit=" + r.exitCode + "): " + r.stderr);
        }
        String b64 = (r.stdout != null ? r.stdout : "").replace("\n", "").replace("\r", "");
        byte[] raw = Base64.getDecoder().decode(b64);
        return new ByteArrayInputStream(raw);
    }

    /**
     * 恢复工作空间。通过 MCP 分块写入 base64 编码的 tar 数据并解压。
     * Hydrates workspace. Chunks base64-encoded tar data via MCP and extracts.
     */
    @Override
    protected void doHydrateWorkspace(InputStream archive) throws Exception {
        String root = arState.getWorkspaceRoot();
        byte[] all = archive.readAllBytes();
        if (all.length == 0) {
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(all);
        mcp.exec("rm -f /tmp/agentscope-ws.b64", null, 30);
        for (int i = 0; i < b64.length(); i += B64_CHUNK) {
            String chunk = b64.substring(i, Math.min(b64.length(), i + B64_CHUNK));
            String py =
                    "import pathlib; pathlib.Path('/tmp/agentscope-ws.b64').open('a').write("
                            + jsonLiteral(chunk)
                            + ")";
            AgentRunMcpChannel.ExecResult chunkRes =
                    mcp.exec("python3 -c " + shellSingleQuote(py), null, 120);
            if (chunkRes.exitCode != 0) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_ARCHIVE_READ_ERROR,
                        "AgentRun chunk write failed: " + chunkRes.stderr);
            }
        }
        String pyFin =
                "import base64,pathlib,subprocess; d="
                        + jsonLiteral(root)
                        + "; raw=base64.standard_b64decode(pathlib.Path('/tmp/agentscope-ws.b64').read_text());"
                        + " subprocess.run(['tar','xf','-','-C',d],input=raw,check=True)";
        AgentRunMcpChannel.ExecResult finRes =
                mcp.exec("python3 -c " + shellSingleQuote(pyFin), null, TAR_TIMEOUT_SECONDS);
        if (finRes.exitCode != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_READ_ERROR,
                    "AgentRun tar extract failed: " + finRes.stderr);
        }
    }

    /**
     * 创建工作空间目录。
     * Sets up the workspace directory.
     */
    @Override
    protected void doSetupWorkspace() throws Exception {
        mcp.exec("mkdir -p " + shellSingleQuote(arState.getWorkspaceRoot()), null, 30);
    }

    /**
     * 销毁工作空间目录。NAS 模式下不销毁（挂载为共享/持久化）。
     * Destroys workspace directory. Skips on NAS mode (mount is shared/persistent).
     */
    @Override
    protected void doDestroyWorkspace() throws Exception {
        if (arState.isWorkspaceOnNas()) {
            // 不要销毁 NAS 支持的工作空间；挂载是共享/持久化的。
            // Do not destroy NAS-backed workspaces; the mount is shared/persistent.
            return;
        }
        try {
            mcp.exec("rm -rf " + shellSingleQuote(arState.getWorkspaceRoot()), null, 30);
        } catch (Exception e) {
            // 尽力而为 / best-effort
        }
    }

    @Override
    protected String getWorkspaceRoot() {
        return arState.getWorkspaceRoot();
    }

    /**
     * 确保沙箱已就绪——如果不存在则创建沙箱并等待其就绪。
     * Ensures the sandbox is ready — creates and waits if not existing.
     */
    private void ensureSandbox() throws Exception {
        String id = arState.getSandboxId();
        if (id == null || id.isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "AgentRun sandbox id is not set — call AgentRunSandboxClient#create or resume");
        }
        try {
            http.getSandbox(id);
            // 已存在；假定可复用（READY/RUNNING）。轮询一次确认。
            // Already exists; assume it's reusable (READY/RUNNING). Poll once to be sure.
            http.waitUntilReady(id, 30);
            return;
        } catch (SandboxException.SandboxRuntimeException e) {
            // 极可能是 404 — 回退到创建
            // Most likely 404 — fall through to create
            if (!isNotFound(e)) {
                throw e;
            }
            arState.setWorkspaceRootReady(false);
        }
        http.createSandbox(id);
        http.waitUntilReady(id, START_WAIT_SECONDS);
    }

    /**
     * 判断异常是否表示沙箱不存在（HTTP 404）。
     * Returns whether the exception indicates sandbox not found (HTTP 404).
     */
    private static boolean isNotFound(Exception e) {
        String m = e.getMessage();
        return m != null && (m.contains("HTTP 404") || m.contains("NotFound"));
    }

    /**
     * 返回工作空间根的相对/绝对路径。
     * Returns the relative or absolute workspace root path.
     */
    private String relativeOrAbsoluteCwd() {
        String root = arState.getWorkspaceRoot();
        return root != null && !root.isBlank() ? root : null;
    }

    /**
     * shell 单引号转义字符串。
     * Shell single-quotes a string.
     */
    private static String shellSingleQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    /**
     * 将字符串转为 Python json 字面量格式（带 shell 单引号兼容转义）。
     * Converts string to Python json literal format (with shell single-quote compatible escaping).
     */
    private static String jsonLiteral(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '\'') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        sb.append('\'');
        return sb.toString();
    }
}
