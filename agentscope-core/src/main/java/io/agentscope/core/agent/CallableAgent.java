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
package io.agentscope.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.message.Msg;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Interface for agents that can be called to process messages.
 *
 * <p>本接口定义了 Agent 的核心调用能力,包括:
 * <ul>
 *   <li>通过 {@link #call(List)} 进行基础消息处理</li>
 *   <li>通过 {@link #call(List, Class)} 和 {@link #call(List, JsonNode)} 生成结构化输出</li>
 * </ul>
 *
 * <p>为便捷方法提供了默认实现,均委托给核心的 {@link #call(List)} 方法。
 *
 * <p>This interface defines the core call capability of agents, including:
 * <ul>
 *   <li>Basic message processing via {@link #call(List)}</li>
 *   <li>Structured output generation via {@link #call(List, Class)} and {@link #call(List, JsonNode)}</li>
 * </ul>
 *
 * <p>Default implementations are provided for convenience methods that delegate
 * to the core {@link #call(List)} method.
 */
public interface CallableAgent {

    /**
     * 在当前状态基础上继续生成响应,不追加新的输入。
     *
     * @return 响应消息
     */
    default Mono<Msg> call() {
        return call(List.of());
    }

    /**
     * 在当前状态基础上,使用 JSON Schema 约束继续生成响应。
     *
     * @param schema 用于定义输出结构的 JSON Schema
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    default Mono<Msg> call(JsonNode schema) {
        return call(List.of(), schema);
    }

    /**
     * 在当前状态基础上,使用结构化模型约束继续生成响应。
     *
     * @param structuredModel 用于定义输出结构的类
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    default Mono<Msg> call(Class<?> structuredModel) {
        return call(List.of(), structuredModel);
    }

    /**
     * 处理单条输入消息并生成响应。
     *
     * @param msg 输入消息
     * @return 响应消息
     */
    default Mono<Msg> call(Msg msg) {
        return call(msg == null ? List.of() : List.of(msg));
    }

    /**
     * 处理单条输入消息并使用结构化模型生成响应。
     *
     * @param msg 输入消息
     * @param structuredModel 用于定义输出结构的类
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    default Mono<Msg> call(Msg msg, Class<?> structuredModel) {
        return call(msg == null ? List.of() : List.of(msg), structuredModel);
    }

    /**
     * 处理单条输入消息并使用 JSON Schema 生成响应。
     *
     * @param msg 输入消息
     * @param schema 用于定义输出结构的 JSON Schema
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    default Mono<Msg> call(Msg msg, JsonNode schema) {
        return call(msg == null ? List.of() : List.of(msg), schema);
    }

    /**
     * 处理多条输入消息(可变参数)并生成响应。
     *
     * @param msgs 输入消息(可变参数)
     * @return 响应消息
     */
    default Mono<Msg> call(Msg... msgs) {
        return call(List.of(msgs));
    }

    /**
     * 处理输入消息列表并生成响应。这是 {@link CallableAgent} 的核心方法,所有其他便捷方法最终都会委派到这里。
     *
     * @param msgs 输入消息列表
     * @return 响应消息
     */
    Mono<Msg> call(List<Msg> msgs);

    /**
     * 处理多条输入消息并使用结构化模型生成响应。
     *
     * <p>结构化模型参数定义了输出数据的预期结构。结构化数据将存放在返回消息的 metadata 字段中。
     *
     * @param msgs 输入消息列表
     * @param structuredModel 用于定义输出结构的类
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    Mono<Msg> call(List<Msg> msgs, Class<?> structuredModel);

    /**
     * 处理多条输入消息并使用 JSON Schema 生成响应。
     *
     * <p>schema 参数定义了输出数据的预期结构。结构化数据将存放在返回消息的 metadata 字段中。
     *
     * @param msgs 输入消息列表
     * @param schema 用于定义输出结构的 JSON Schema
     * @return 响应消息,结构化数据存放在 metadata 中
     */
    Mono<Msg> call(List<Msg> msgs, JsonNode schema);
}
