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
 * 技能系统提示注入钩子，在 {@link PreCallEvent} 中通过
 * {@link PreCallEvent#appendSystemContent(String)} 将当前可用的技能目录信息
 * 追加到统一的系统消息中，使 LLM 了解可以动态加载哪些技能。
 *
 * <p><b>执行时机：</b><br>
 * 优先级 {@link #SKILL_HOOK_PRIORITY} = 85，在典型的 {@code HarnessAgent}
 * 编排中，此钩子在 {@code SubagentsHook}（优先级 80）之后、
 * {@code WorkspaceContextHook}（优先级 900）之前运行。
 * 提示追加顺序为：基础系统提示 → 子智能体 → 技能 → 工作空间上下文。
 *
 * <p><b>关键行为：</b>
 * <ul>
 *   <li>技能提示被追加到 <em>临时</em> 系统消息中，不会持久化到智能体的 {@code Memory}</li>
 *   <li>每次 {@code PreCallEvent} 触发时都会重新生成，反映技能的最新注册状态</li>
 *   <li>如果当前没有已注册的技能，提示内容为空字符串，跳过追加</li>
 * </ul>
 *
 * @see SkillBox#getSkillPrompt()
 * @see io.agentscope.core.hook.Hook
 */
public class SkillHook implements Hook {

    /**
     * 钩子优先级，在内置钩子链中位于子智能体提示注入之后、工作空间上下文注入之前。
     *
     * <p>当前值 85 确保执行顺序为：基础提示(0) → 子智能体(80) → 技能(85) → 工作空间上下文(900)。
     */
    public static final int SKILL_HOOK_PRIORITY = 85;

    private final SkillBox skillBox;

    /**
     * 创建一个技能钩子实例。
     *
     * @param skillBox 技能箱实例，用于获取当前可用的技能系统提示
     */
    public SkillHook(SkillBox skillBox) {
        this.skillBox = skillBox;
    }

    /**
     * 处理钩子事件，将技能系统提示注入到 PreCallEvent 中。
     *
     * <p>如果当前事件是 {@link PreCallEvent} 且有可用的技能提示，
     * 则通过 {@link PreCallEvent#appendSystemContent(String)} 追加到系统消息中。
     *
     * @param event 要处理的事件对象
     * @param <T>   事件的具体类型
     * @return 处理完成后的事件对象的 Mono
     */
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
