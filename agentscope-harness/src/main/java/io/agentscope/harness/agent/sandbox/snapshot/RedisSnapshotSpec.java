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
package io.agentscope.harness.agent.sandbox.snapshot;

import redis.clients.jedis.UnifiedJedis;

/**
 * Redis 后端快照存储的便捷 {@link SandboxSnapshotSpec} 实现。
 * <p>
 * Convenience {@link SandboxSnapshotSpec} for Redis-backed snapshot storage.
 */
public class RedisSnapshotSpec extends RemoteSnapshotSpec {

    /**
     * 创建 Redis 快照规范。
     * <p>
     * Creates a Redis snapshot spec.
     *
     * @param jedis      初始化后的 jedis 客户端
     * @param keyPrefix  Redis 键前缀（可选）
     * @param ttlSeconds 可选的 TTL 秒数（null 或负数表示无 TTL）
     */
    public RedisSnapshotSpec(UnifiedJedis jedis, String keyPrefix, Integer ttlSeconds) {
        super(new RedisRemoteSnapshotClient(jedis, keyPrefix, ttlSeconds));
    }
}
