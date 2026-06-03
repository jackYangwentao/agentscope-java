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
 * 配置 ReActAgent 应为哪些组件管理状态持久化。
 *
 * <p>ReActAgent 有四个可持久化的状态组件：
 * <ul>
 *   <li><b>memory（记忆）</b> — 对话历史与上下文</li>
 *   <li><b>toolkit（工具包）</b> — 工具的激活组状态</li>
 *   <li><b>planNotebook（计划笔记本）</b> — 当前计划和子任务进展</li>
 *   <li><b>statefulTools（有状态工具）</b> — 有状态工具的内部状态</li>
 * </ul>
 * 用户可以通过此配置选择性地对特定组件启用或禁用自动状态管理。</p>
 *
 * <p>使用示例：</p>
 *
 * <pre>{@code
 * // Default: manage all components（默认：管理所有组件）
 * ReActAgent agent = ReActAgent.builder()
 *     .name("assistant")
 *     .model(model)
 *     .memory(memory)
 *     .planNotebook(planNotebook)
 *     .build();
 *
 * // Exclude PlanNotebook: user manages it independently（排除 PlanNotebook，由用户自行管理）
 * ReActAgent agent = ReActAgent.builder()
 *     .name("assistant")
 *     .model(model)
 *     .memory(memory)
 *     .planNotebook(planNotebook)
 *     .statePersistence(StatePersistence.builder()
 *         .planNotebookManaged(false)
 *         .build())
 *     .build();
 *
 * // Only manage Memory（仅管理记忆组件）
 * ReActAgent agent = ReActAgent.builder()
 *     .name("assistant")
 *     .model(model)
 *     .memory(memory)
 *     .statePersistence(StatePersistence.memoryOnly())
 *     .build();
 *
 * // Don't manage any components, user fully controls（完全由用户自行管理）
 * ReActAgent agent = ReActAgent.builder()
 *     .name("assistant")
 *     .model(model)
 *     .statePersistence(StatePersistence.none())
 *     .build();
 * }</pre>
 *
 * @param memoryManaged 是否管理 Memory（记忆）组件的状态持久化
 * @param toolkitManaged 是否管理 Toolkit（工具包）的 activeGroups 状态
 * @param planNotebookManaged 是否管理 PlanNotebook（计划笔记本）的状态
 * @param statefulToolsManaged 是否管理有状态工具的内部状态
 * @see StateModule
 * @see io.agentscope.core.ReActAgent
 */
public record StatePersistence(
        boolean memoryManaged,
        boolean toolkitManaged,
        boolean planNotebookManaged,
        boolean statefulToolsManaged) {

    /** 默认配置：管理所有组件的状态持久化。 */
    public static StatePersistence all() {
        return new StatePersistence(true, true, true, true);
    }

    /** 不管理任何组件的状态持久化，完全由用户自行控制。 */
    public static StatePersistence none() {
        return new StatePersistence(false, false, false, false);
    }

    /** 仅管理 Memory（记忆）组件的状态持久化。 */
    public static StatePersistence memoryOnly() {
        return new StatePersistence(true, false, false, false);
    }

    /**
     * 创建 StatePersistence 构建器，所有组件默认启用。
     *
     * @return 新的 Builder 实例，所有组件默认为 true（已管理）
     */
    public static Builder builder() {
        return new Builder();
    }

    /** StatePersistence 构建器，支持选择性禁用特定组件的自动状态管理。 */
    public static class Builder {

        /** 是否管理 Memory 状态，默认管理。 */
        private boolean memoryManaged = true;

        /** 是否管理 Toolkit 状态，默认管理。 */
        private boolean toolkitManaged = true;

        /** 是否管理 PlanNotebook 状态，默认管理。 */
        private boolean planNotebookManaged = true;

        /** 是否管理有状态工具的状态，默认管理。 */
        private boolean statefulToolsManaged = true;

        /**
         * 设置是否管理 Memory（记忆）组件的状态。
         *
         * @param managed true 由 ReActAgent 自动管理，false 由用户自行管理
         * @return 当前构建器实例（链式调用）
         */
        public Builder memoryManaged(boolean managed) {
            this.memoryManaged = managed;
            return this;
        }

        /**
         * 设置是否管理 Toolkit（工具包）的 activeGroups 状态。
         *
         * @param managed true 由 ReActAgent 自动管理，false 由用户自行管理
         * @return 当前构建器实例（链式调用）
         */
        public Builder toolkitManaged(boolean managed) {
            this.toolkitManaged = managed;
            return this;
        }

        /**
         * 设置是否管理 PlanNotebook（计划笔记本）的状态。
         *
         * @param managed true 由 ReActAgent 自动管理，false 由用户自行管理
         * @return 当前构建器实例（链式调用）
         */
        public Builder planNotebookManaged(boolean managed) {
            this.planNotebookManaged = managed;
            return this;
        }

        /**
         * 设置是否管理有状态工具的内部状态。
         *
         * @param managed true 由 ReActAgent 自动管理，false 由用户自行管理
         * @return 当前构建器实例（链式调用）
         */
        public Builder statefulToolsManaged(boolean managed) {
            this.statefulToolsManaged = managed;
            return this;
        }

        /**
         * 使用已配置的设置构建 StatePersistence 实例。
         *
         * @return 新的 StatePersistence 实例
         */
        public StatePersistence build() {
            return new StatePersistence(
                    memoryManaged, toolkitManaged, planNotebookManaged, statefulToolsManaged);
        }
    }
}
