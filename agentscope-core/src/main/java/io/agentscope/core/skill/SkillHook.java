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
package io.agentscope.core.skill;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreCallEvent;
import reactor.core.publisher.Mono;

/**
 * 在 {@link PreCallEvent} 上通过 {@link PreCallEvent#appendSystemContent(String)}
 * 将技能目录提示注入到统一的系统消息中。
 *
 * <p>使用 {@link #SKILL_HOOK_PRIORITY} 优先级，在典型的 {@code HarnessAgent} 编排中，
 * 此钩子在 {@code SubagentsHook} (80) 之后、{@code WorkspaceContextHook} (900) 之前运行，
 * 追加顺序为：基础提示 → 子智能体 → 技能 → 工作空间上下文。
 *
 * <p>技能提示被追加到临时系统消息中，永远不会存储在智能体的持久化 {@code Memory} 中。
 */
public class SkillHook implements Hook {

    /**
     * Runs after subagent prompt injection and before workspace context injection in the default
     * harness hook chain.
     */
    public static final int SKILL_HOOK_PRIORITY = 85;

    private final SkillBox skillBox;

    public SkillHook(SkillBox skillBox) {
        this.skillBox = skillBox;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreCallEvent preCallEvent) {
            String skillPrompt = skillBox.getSkillPrompt();
            if (skillPrompt != null && !skillPrompt.isEmpty()) {
                preCallEvent.appendSystemContent(skillPrompt);
            }
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return SKILL_HOOK_PRIORITY;
    }
}
