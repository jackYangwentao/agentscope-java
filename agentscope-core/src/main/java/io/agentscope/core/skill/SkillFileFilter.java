/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.core.skill;

/**
 * 技能资源文件上传过滤器，用于判断技能中的某个资源文件是否应当被写入磁盘。
 *
 * <p>在 {@link SkillBox#uploadSkillFiles()} 执行时，系统会遍历每个已注册技能的所有资源路径，
 * 并调用此过滤器逐一判断。只有被过滤器接受的资源才会被实际写入上传目录。
 *
 * <p><b>典型用途：</b>
 * <ul>
 *   <li>限制只上传 <code>scripts/</code> 和 <code>assets/</code> 目录下的文件</li>
 *   <li>限制只上传特定扩展名（如 <code>.py</code>、<code>.js</code>）的文件</li>
 *   <li>实现自定义的安全策略，阻止敏感文件被写出</li>
 * </ul>
 *
 * <p>{@link SkillBox.CodeExecutionBuilder} 内部使用 {@code DefaultSkillFileFilter}
 * 实现默认过滤逻辑，并允许通过构建器配置包含的文件夹和扩展名。
 *
 * @see SkillBox
 * @see SkillBox.CodeExecutionBuilder
 */
@FunctionalInterface
public interface SkillFileFilter {

    /**
     * 判断指定的资源路径是否应当被接受并上传到磁盘。
     *
     * <p>资源路径是相对于技能根目录的相对路径，例如 {@code "scripts/run.py"}、
     * {@code "assets/config.json"}。实现类应基于路径模式、扩展名或其他条件做出判断。
     *
     * @param resourcePath 资源路径（相对于技能根目录的相对路径，不可为 null）
     * @return true 表示该资源应被上传；false 表示跳过该资源
     */
    boolean accept(String resourcePath);

    /**
     * 返回一个接受所有文件的过滤器。
     *
     * <p>当不需要对上传文件进行任何筛选时使用此方法。它会无条件接受每一个资源路径。
     *
     * @return 一个接收所有文件的过滤器实例
     */
    static SkillFileFilter acceptAll() {
        return path -> true;
    }
}
