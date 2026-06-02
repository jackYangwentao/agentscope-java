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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import redis.clients.jedis.UnifiedJedis;

/**
 * 基于 Redis 二进制值的 {@link RemoteSnapshotClient} 实现。
 * <p>
 * {@link RemoteSnapshotClient} backed by Redis binary values.
 */
public class RedisRemoteSnapshotClient implements RemoteSnapshotClient {

    private final UnifiedJedis jedis;
    private final String keyPrefix;
    private final Integer ttlSeconds;

    /**
     * 创建 Redis 后端的快照客户端。
     * <p>
     * Creates a Redis-backed snapshot client.
     *
     * @param jedis      初始化后的 jedis 客户端
     * @param keyPrefix  Redis 键前缀（可选）
     * @param ttlSeconds 可选的 TTL 秒数（null 或负数表示无 TTL）
     */
    public RedisRemoteSnapshotClient(UnifiedJedis jedis, String keyPrefix, Integer ttlSeconds) {
        this.jedis = Objects.requireNonNull(jedis, "jedis must not be null");
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.ttlSeconds = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds : null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 将快照数据上传到 Redis，可选设置 TTL。
     */
    @Override
    public void upload(String snapshotId, InputStream data) throws Exception {
        byte[] key = redisKey(snapshotId);
        byte[] value = data.readAllBytes();
        jedis.set(key, value);
        if (ttlSeconds != null) {
            jedis.expire(key, ttlSeconds);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 从 Redis 下载快照数据。
     */
    @Override
    public InputStream download(String snapshotId) throws Exception {
        byte[] data = jedis.get(redisKey(snapshotId));
        if (data == null) {
            throw new FileNotFoundException(
                    "Snapshot not found in Redis: " + composeKey(snapshotId));
        }
        return new ByteArrayInputStream(data);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 检查快照在 Redis 中是否存在。
     */
    @Override
    public boolean exists(String snapshotId) throws Exception {
        return jedis.exists(redisKey(snapshotId));
    }

    /**
     * 构造 Redis 键的字节数组表示。
     * <p>
     * Build the Redis key as a byte array.
     */
    private byte[] redisKey(String snapshotId) {
        return composeKey(snapshotId).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 构造完整的 Redis 键字符串。
     * <p>
     * Build the full Redis key string.
     */
    private String composeKey(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be blank");
        }
        return keyPrefix + snapshotId + ".tar";
    }

    /**
     * 标准化键前缀（默认前缀为 "agentscope:sandbox:snapshots:"）。
     * <p>
     * Normalize the key prefix (default: "agentscope:sandbox:snapshots:").
     */
    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "agentscope:sandbox:snapshots:";
        }
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }
}
