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

/**
 * 可持久化状态对象的标记接口。
 *
 * <p>实现此接口的类可以被 {@link io.agentscope.core.session.Session} 实现序列化和存储。
 * 推荐对简单的状态对象使用 Java Record。
 *
 * <p>现有的领域对象（如 {@link io.agentscope.core.message.Msg}）可以直接实现此接口以避免转换开销。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // Using a Record
 * public record AgentMetaState(
 *     String id,
 *     String name,
 *     String description
 * ) implements State {}
 *
 * // Existing class implementing State
 * public class Msg implements State {
 *     // existing fields and methods
 * }
 * }</pre>
 *
 * @see StateModule
 * @see io.agentscope.core.session.Session
 */
public interface State {}
