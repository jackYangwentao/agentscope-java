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
package io.agentscope.harness.agent.filesystem.sandbox;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.ExecuteResponse;

/**
 * 添加 shell 命令执行（沙箱或远程主机）能力的文件系统抽象。
 *
 * <p>扩展 {@link AbstractFilesystem}，增加了 {@link #execute} 和 {@link #id()}。
 */
public interface AbstractSandboxFilesystem extends AbstractFilesystem {

    /**
     * 此文件系统/沙箱实例的唯一标识符。
     *
     * @return id 字符串
     */
    String id();

    /**
     * 在支持此文件系统的环境中执行 shell 命令。
     *
     * @param runtimeContext 每次调用的代理上下文；不可用时可为 {@code null}
     * @param command 要执行的完整 shell 命令字符串
     * @param timeoutSeconds 等待命令完成的最大时间（秒）；
     *                       {@code null} 使用文件系统的默认超时
     * @return 包含合并输出、退出码和截断标志的 ExecuteResponse
     */
    ExecuteResponse execute(RuntimeContext runtimeContext, String command, Integer timeoutSeconds);
}
