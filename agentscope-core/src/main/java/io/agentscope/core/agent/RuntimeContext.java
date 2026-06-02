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

import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.tool.ContextStore;
import io.agentscope.core.tool.ToolExecutionContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-call metadata for an agent run: session-scoped fields plus a thread-safe attribute bag and
 * an optional {@link ToolExecutionContext} (tool-POJO / DI layer).
 *
 * <p>单次 Agent 调用的元数据:会话作用域字段,加上一个线程安全的属性包,
 * 以及一个可选的 {@link ToolExecutionContext}(工具 POJO / DI 层)。
 *
 * <p>属性不会被持久化。在单次 {@code call} 调用期间,Hook 和工具可读写同一个实例。
 *
 * <p>Attributes are not persisted. Hooks and tools may read and update the same instance for the
 * duration of a single {@code call}.
 */
public class RuntimeContext {

    private static final String TYPED_DEFAULT_KEY = "";

    private final String sessionId;
    private final String userId;
    private final Session session;
    private final SessionKey sessionKey;

    /** 以 String 为键的扩展属性(遗留及通用扩展用途)。 */
    private final ConcurrentMap<String, Object> stringAttributes;

    /**
     * 类型化层:class -&gt; (key -&gt; value)。单例类型访问请使用 {@link #TYPED_DEFAULT_KEY}。
     */
    private final ConcurrentMap<Class<?>, ConcurrentMap<String, Object>> typedAttributes;

    private final ToolExecutionContext toolExecutionContext;

    private RuntimeContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.session = builder.session;
        this.sessionKey = builder.sessionKey;
        this.stringAttributes = new ConcurrentHashMap<>();
        this.typedAttributes = new ConcurrentHashMap<>();
        this.toolExecutionContext = builder.toolExecutionContext;
        if (builder.stringExtras != null) {
            this.stringAttributes.putAll(builder.stringExtras);
        }
        for (Map.Entry<Class<?>, Object> e : builder.typedSingletons.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<Object> type = (Class<Object>) e.getKey();
            putValue(TYPED_DEFAULT_KEY, type, e.getValue());
        }
    }

    /**
     * 构造一个浅层、可变的空 Context(session 字段为 null,属性 map 为空,无工具 Context)。
     */
    public static RuntimeContext empty() {
        return new Builder().build();
    }

    /** @return 会话 ID */
    public String getSessionId() {
        return sessionId;
    }

    /** @return 用户 ID */
    public String getUserId() {
        return userId;
    }

    /** @return 关联的 {@link Session} */
    public Session getSession() {
        return session;
    }

    /** @return 关联的 {@link SessionKey} */
    public SessionKey getSessionKey() {
        return sessionKey;
    }

    /**
     * 返回构建时提供的工具执行 Context(若有)。
     *
     * <p>不包含运行时属性映射,请使用 {@link #asToolExecutionContext()}。
     */
    public ToolExecutionContext getToolExecutionContext() {
        return toolExecutionContext;
    }

    /**
     * 按 String 键获取属性。
     *
     * @param key 键名,null 时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (key == null) {
            return null;
        }
        return (T) stringAttributes.get(key);
    }

    /**
     * 按 String 键写入属性。value 为 null 时等价于移除该键。
     */
    public void put(String key, Object value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            stringAttributes.remove(key);
        } else {
            stringAttributes.put(key, value);
        }
    }

    /**
     * 按类型(单例)获取属性。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        if (type == null) {
            return null;
        }
        T v = getValue(TYPED_DEFAULT_KEY, type);
        if (v != null) {
            return v;
        }
        // Allow accessing this RuntimeContext itself
        if (type == RuntimeContext.class) {
            return (T) this;
        }
        return null;
    }

    /**
     * 按类型(单例)写入属性。value 为 null 时移除该类型。
     */
    public <T> void put(Class<T> type, T value) {
        if (type == null) {
            return;
        }
        if (value == null) {
            removeTyped(type, TYPED_DEFAULT_KEY);
        } else {
            putValue(TYPED_DEFAULT_KEY, type, value);
        }
    }

    /**
     * 按 String 键 + 类型获取属性。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        if (key == null || type == null) {
            return null;
        }
        T v = getValue(key, type);
        if (v != null) {
            return v;
        }
        if (TYPED_DEFAULT_KEY.equals(key) && type == RuntimeContext.class) {
            return (T) this;
        }
        return null;
    }

    /**
     * 按 String 键 + 类型写入属性。value 为 null 时移除。
     */
    public <T> void put(String key, Class<T> type, T value) {
        if (key == null || type == null) {
            return;
        }
        if (value == null) {
            removeTyped(type, key);
        } else {
            putValue(key, type, value);
        }
    }

    /**
     * 获取 String 键扩展属性的视图(可变),修改返回值会影响本 Context。
     *
     * <p>不包含类型化 {@link #get(Class)} 值,类型化访问请使用类型化 API。
     */
    public Map<String, Object> getExtra() {
        return stringAttributes;
    }

    private <T> void putValue(String key, Class<T> type, Object value) {
        typedAttributes.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    private <T> T getValue(String key, Class<T> type) {
        Map<String, Object> m = typedAttributes.get(type);
        if (m == null) {
            return null;
        }
        Object o = m.get(key);
        if (o == null) {
            return null;
        }
        return type.isInstance(o) ? type.cast(o) : null;
    }

    private <T> void removeTyped(Class<T> type, String key) {
        Map<String, Object> m = typedAttributes.get(type);
        if (m == null) {
            return;
        }
        m.remove(key);
        if (m.isEmpty()) {
            typedAttributes.remove(type);
        }
    }

    /**
     * 将本 Context 的数据合并到 {@link ToolExecutionContext} 以供工具调用使用。
     *
     * <p>顺序:本实例优先注册并暴露(在 {@link ToolExecutionContext#merge} 中优先级最高),
     * 然后是承载类型化和 String 属性的 {@link ContextStore},
     * 最后是嵌套的 {@link #getToolExecutionContext()}(若有)中的 store。
     */
    public ToolExecutionContext asToolExecutionContext() {
        ToolExecutionContext.Builder b = ToolExecutionContext.builder();
        b.addStore(new DefaultMutableContextStore(this));
        if (toolExecutionContext != null) {
            for (ContextStore s : toolExecutionContext.getStores()) {
                if (s != null) {
                    b.addStore(s);
                }
            }
        }
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String userId;
        private Session session;
        private SessionKey sessionKey;
        private Map<String, Object> stringExtras;
        private final Map<Class<?>, Object> typedSingletons = new HashMap<>();
        private ToolExecutionContext toolExecutionContext;

        /** @param sessionId 会话 ID */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /** @param userId 用户 ID */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /** @param session 关联的 {@link Session} */
        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        /** @param sessionKey 关联的 {@link SessionKey} */
        public Builder sessionKey(SessionKey sessionKey) {
            this.sessionKey = sessionKey;
            return this;
        }

        /**
         * 在 Builder 阶段预置一个 String 键扩展属性(用于 {@link #getExtra()})。
         */
        public Builder put(String key, Object value) {
            if (this.stringExtras == null) {
                this.stringExtras = new ConcurrentHashMap<>();
            }
            this.stringExtras.put(key, value);
            return this;
        }

        /**
         * 批量预置 String 键扩展属性。
         */
        public Builder putAll(Map<String, Object> extras) {
            if (extras == null || extras.isEmpty()) {
                return this;
            }
            if (this.stringExtras == null) {
                this.stringExtras = new ConcurrentHashMap<>();
            }
            this.stringExtras.putAll(extras);
            return this;
        }

        /**
         * 在 Builder 阶段预置一个类型化单例属性。
         */
        public <T> Builder put(Class<T> type, T value) {
            if (type != null) {
                this.typedSingletons.put(type, value);
            }
            return this;
        }

        /**
         * 嵌套一个 {@link ToolExecutionContext}(例如 Agent Builder 级别的工具 DI),
         * 在 {@link #asToolExecutionContext()} 中其优先级低于运行时属性。
         */
        public Builder toolExecutionContext(ToolExecutionContext toolExecutionContext) {
            this.toolExecutionContext = toolExecutionContext;
            return this;
        }

        public RuntimeContext build() {
            return new RuntimeContext(this);
        }
    }

    /**
     * 工具栈使用的本 {@link RuntimeContext} 合并视图:先查类型化,再查 String 键(遗留的
     * {@link #get(String)} 键),最后委托给被委派的 store。
     */
    private static final class DefaultMutableContextStore implements ContextStore {

        private final RuntimeContext runtimeContext;

        private DefaultMutableContextStore(RuntimeContext runtimeContext) {
            this.runtimeContext = runtimeContext;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            T t = runtimeContext.getValue(key, type);
            if (t != null) {
                return t;
            }
            if (TYPED_DEFAULT_KEY.equals(key) && type == RuntimeContext.class) {
                return (T) runtimeContext;
            }
            Object fromString = runtimeContext.stringAttributes.get(key);
            if (type.isInstance(fromString)) {
                return (T) fromString;
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Class<T> type) {
            T t = get(TYPED_DEFAULT_KEY, type);
            if (t != null) {
                return t;
            }
            if (type == RuntimeContext.class) {
                return (T) runtimeContext;
            }
            return null;
        }

        @Override
        public boolean contains(String key, Class<?> type) {
            return get(key, type) != null;
        }

        @Override
        public boolean contains(Class<?> type) {
            return get(type) != null;
        }
    }
}
