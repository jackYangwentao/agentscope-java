/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.state;

import java.util.List;

/**
 * 工具集激活组的状态记录。
 *
 * <p>该记录捕获激活的工具组配置以进行持久化。工具集本身是无状态的，但其 activeGroups 配置需要持久化。
 * 此状态由 {@link io.agentscope.core.ReActAgent} 管理。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * ToolkitState state = new ToolkitState(List.of("web", "file", "calculator"));
 * session.save(sessionKey, "toolkit_activeGroups", state);
 *
 * // Later, restore the state
 * Optional<ToolkitState> loaded = session.get(sessionKey, "toolkit_activeGroups", ToolkitState.class);
 * loaded.ifPresent(s -> toolkit.setActiveGroups(s.activeGroups()));
 * }</pre>
 *
 * @param activeGroups 当前激活的工具组名称列表
 * @see State
 * @see io.agentscope.core.tool.Toolkit
 */
public record ToolkitState(List<String> activeGroups) implements State {}
