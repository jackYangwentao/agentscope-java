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
 * 由 E2B 云沙箱驱动的 {@link io.agentscope.harness.agent.sandbox.Sandbox} 实现。
 * <p>
 * 执行使用 envd {@code process.Process/Start} 通过 HTTPS（Connect+protobuf）进行。
 * 对于 {@link E2bPersistenceMode#TAR}，工作空间 tar 流作为二进制 stdout 传输；
 * {@link E2bPersistenceMode#NATIVE_SNAPSHOT} 使用平台快照 API 和 {@link E2bSnapshotRefs}
 * 标记字节（位于 Harness 快照流中）。
 * <p>
 * {@link io.agentscope.harness.agent.sandbox.Sandbox} backed by E2B cloud sandboxes.
 * Execution uses envd {@code process.Process/Start} over HTTPS (Connect+protobuf). Workspace
 * tar is streamed as binary stdout for {@link E2bPersistenceMode#TAR}; {@link
 * E2bPersistenceMode#NATIVE_SNAPSHOT} uses the platform snapshot API and {@link E2bSnapshotRefs}
 * marker bytes in the Harness snapshot stream.
 */
public class E2bSandbox extends AbstractBaseSandbox {

    /** 日志记录器。Logger. */
    private static final Logger log = LoggerFactory.getLogger(E2bSandbox.class);

    /** tar 操作的超时秒数。Tar operation timeout in seconds. */
    private static final int TAR_TIMEOUT_SECONDS = 300;

    /** Base64 分块大小。Base64 chunk size. */
    private static final int B64_CHUNK = 4000;

    /** E2B 沙箱状态。E2B sandbox state. */
    private final E2bSandboxState e2bState;

    /** E2B 沙箱客户端选项。E2B sandbox client options. */
    private final E2bSandboxClientOptions opt;

    /** E2B 平台 HTTP 客户端。E2B platform HTTP client. */
    private final E2bPlatformHttp platform;

    /** envd 进程客户端（懒初始化）。Envd process client (lazy). */
    private E2bEnvdProcessClient envd;

    /**
     * 使用给定状态和选项构造 E2bSandbox 实例。
     * Constructs an E2bSandbox instance with the given state and options.
     *
     * @param state E2B 沙箱状态 / E2B sandbox state
     * @param opt   E2B 沙箱客户端选项 / E2B sandbox client options
     */
    public E2bSandbox(E2bSandboxState state, E2bSandboxClientOptions opt) {
        super(state);
        this.e2bState = state;
        this.opt = opt != null ? opt : new E2bSandboxClientOptions();
        this.platform = new E2bPlatformHttp(this.opt);
    }

    /**
     * 启动沙箱，如果尚未存在则创建新沙箱或连接到已有沙箱。
     * Starts the sandbox, creating a new one or connecting to an existing one.
     */
    @Override
    public void start() throws Exception {
        if (WorkspaceMountSupport.hasBindMounts(e2bState.getWorkspaceSpec())) {
            log.warn(
                    "[sandbox-e2b] WorkspaceSpec contains bind_mount entries; "
                            + "E2B does not apply host bind mounts — paths are not mounted.");
        }
        ensureSandbox();
        super.start();
    }

    /**
     * 关闭沙箱并释放后端资源。
     * Shuts down the sandbox and releases backend resources.
     */
    @Override
    public void shutdown() throws Exception {
        if (!e2bState.isSandboxOwned()) {
            return;
        }
        String id = e2bState.getSandboxId();
        if (id != null && !id.isBlank()) {
            platform.killSandbox(id);
        }
    }

    @Override
    protected ExecResult doExec(RuntimeContext runtimeContext, String command, int timeoutSeconds)
            throws Exception {
        return envd().runShell(e2bState, getWorkspaceRoot(), command, timeoutSeconds);
    }

    /**
     * 持久化工作空间：如果是 NATIVE_SNAPSHOT 模式则使用平台快照 API，否则导出 tar 流。
     * Persists workspace: uses platform snapshot API for NATIVE_SNAPSHOT mode, or exports tar stream.
     */
    @Override
    protected InputStream doPersistWorkspace() throws Exception {
        if (e2bState.getPersistenceMode() == E2bPersistenceMode.NATIVE_SNAPSHOT) {
            JsonNode snap = platform.createSandboxSnapshot(e2bState.getSandboxId());
            String id = snap.path("snapshotID").asText("");
            if (id.isBlank()) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_ARCHIVE_WRITE_ERROR,
                        "E2B snapshot response missing snapshotID: " + snap);
            }
            return new ByteArrayInputStream(E2bSnapshotRefs.encodeSnapshotId(id));
        }
        String root = e2bState.getWorkspaceRoot();
        StringBuilder script = new StringBuilder("tar ");
        for (String ex :
                WorkspaceMountSupport.tarExcludeArgsForBindMounts(e2bState.getWorkspaceSpec())) {
            script.append(ex).append(' ');
        }
        script.append("-cf - -C ").append(shellSingleQuote(root)).append(" .");
        String cmd = script.toString();
        byte[] tar = envd().runShellBinaryStdout(e2bState, root, cmd, TAR_TIMEOUT_SECONDS);
        return new ByteArrayInputStream(tar);
    }

    /**
     * 恢复工作空间：如果是原生快照则从快照模板恢复，否则从 tar 流恢复。
     * Hydrates workspace: restores from snapshot template if native, or from tar stream.
     */
    @Override
    protected void doHydrateWorkspace(InputStream archive) throws Exception {
        byte[] all = archive.readAllBytes();
        String nativeId = E2bSnapshotRefs.decodeSnapshotIdIfPresent(all);
        if (nativeId != null && !nativeId.isBlank()) {
            restoreSandboxFromSnapshotTemplate(nativeId);
            return;
        }
        String root = e2bState.getWorkspaceRoot();
        String b64 = Base64.getEncoder().encodeToString(all);
        envd().runShell(e2bState, root, "rm -f /tmp/agentscope-ws.b64", 30);
        ObjectMapper om = new ObjectMapper();
        for (int i = 0; i < b64.length(); i += B64_CHUNK) {
            String chunk = b64.substring(i, Math.min(b64.length(), i + B64_CHUNK));
            String lit = om.writeValueAsString(chunk);
            String py =
                    "import pathlib; pathlib.Path('/tmp/agentscope-ws.b64').open('a').write("
                            + lit
                            + ")";
            envd().runShell(e2bState, root, "python3 -c " + shellSingleQuote(py), 120);
        }
        String pyFin =
                "import base64,pathlib,subprocess; d="
                        + om.writeValueAsString(root)
                        + "; raw=base64.standard_b64decode(pathlib.Path('/tmp/agentscope-ws.b64').read_text());"
                        + " subprocess.run(['tar','xf','-','-C',d],input=raw,check=True)";
        envd().runShell(
                        e2bState,
                        root,
                        "python3 -c " + shellSingleQuote(pyFin),
                        TAR_TIMEOUT_SECONDS);
    }

    /**
     * 设置工作空间目录。
     * Sets up the workspace directory.
     */
    @Override
    protected void doSetupWorkspace() throws Exception {
        envd().runShell(
                        e2bState,
                        getWorkspaceRoot(),
                        "mkdir -p " + shellSingleQuote(e2bState.getWorkspaceRoot()),
                        30);
    }

    /**
     * 销毁工作空间目录（尽力而为）。
     * Destroys the workspace directory (best-effort).
     */
    @Override
    protected void doDestroyWorkspace() throws Exception {
        try {
            envd().runShell(
                            e2bState,
                            getWorkspaceRoot(),
                            "rm -rf " + shellSingleQuote(e2bState.getWorkspaceRoot()),
                            30);
        } catch (Exception e) {
            log.debug("[sandbox-e2b] destroy workspace best-effort: {}", e.getMessage());
        }
    }

    @Override
    protected String getWorkspaceRoot() {
        return e2bState.getWorkspaceRoot();
    }

    /**
     * 确保沙箱已就绪——如果已有 sandboxId 则连接，否则创建新沙箱。
     * Ensures the sandbox is ready — connects if sandboxId exists, or creates a new one.
     */
    private void ensureSandbox() throws Exception {
        if (e2bState.getSandboxId() == null || e2bState.getSandboxId().isBlank()) {
            JsonNode n =
                    platform.createSandbox(
                            e2bState.getTemplateId(), opt.getSandboxTimeoutSeconds());
            platform.applySandboxFields(e2bState, n);
            applyDefaultDomain();
            envd = null;
            return;
        }
        try {
            JsonNode n =
                    platform.connectSandbox(
                            e2bState.getSandboxId(), opt.getSandboxTimeoutSeconds());
            platform.applySandboxFields(e2bState, n);
        } catch (Exception e) {
            log.warn("[sandbox-e2b] connect failed, recreating sandbox: {}", e.getMessage());
            e2bState.setWorkspaceRootReady(false);
            JsonNode n =
                    platform.createSandbox(
                            e2bState.getTemplateId(), opt.getSandboxTimeoutSeconds());
            platform.applySandboxFields(e2bState, n);
        }
        applyDefaultDomain();
        envd = null;
    }

    /**
     * 如果状态中未设置域，则应用选项中的默认域。
     * Applies the default domain from options if not set in state.
     */
    private void applyDefaultDomain() {
        if (e2bState.getSandboxDomain() == null || e2bState.getSandboxDomain().isBlank()) {
            e2bState.setSandboxDomain(opt.getDomain());
        }
    }

    /**
     * 从快照模板恢复沙箱，替换当前的沙箱 ID。
     * Restores sandbox from a snapshot template, replacing the current sandbox ID.
     */
    private void restoreSandboxFromSnapshotTemplate(String snapshotTemplateId) throws Exception {
        String oldId = e2bState.getSandboxId();
        JsonNode created =
                platform.createSandbox(snapshotTemplateId, opt.getSandboxTimeoutSeconds());
        platform.applySandboxFields(e2bState, created);
        applyDefaultDomain();
        if (e2bState.isSandboxOwned()
                && oldId != null
                && !oldId.isBlank()
                && !oldId.equals(e2bState.getSandboxId())) {
            try {
                platform.killSandbox(oldId);
            } catch (Exception e) {
                log.debug(
                        "[sandbox-e2b] failed to delete old sandbox {}: {}", oldId, e.getMessage());
            }
        }
        envd = null;
    }

    /**
     * 获取 envd 进程客户端（双重检查锁定懒初始化）。
     * Gets the envd process client (double-checked locking lazy init).
     */
    private E2bEnvdProcessClient envd() throws Exception {
        E2bEnvdProcessClient c = envd;
        if (c == null) {
            synchronized (this) {
                if (envd == null) {
                    envd = new E2bEnvdProcessClient(opt);
                }
                c = envd;
            }
        }
        return c;
    }

    /**
     * shell 单引号转义字符串。
     * Shell single-quotes a string.
     */
    private static String shellSingleQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}
