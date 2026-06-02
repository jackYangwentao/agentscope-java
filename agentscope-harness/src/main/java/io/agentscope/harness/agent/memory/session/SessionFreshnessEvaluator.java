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
package io.agentscope.harness.agent.memory.session;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 评估会话是否仍然"新鲜"或应重置。
 *
 * <p>灵感来自 agentscope-claw 的 SessionFreshnessEvaluator。支持两种重置策略：
 * <ul>
 *   <li><b>每日重置：</b>会话在每天配置的小时后重置</li>
 *   <li><b>空闲超时：</b>会话在超过阈值的不活动时间后重置</li>
 * </ul>
 */
public class SessionFreshnessEvaluator {

    private final int dailyResetHour;
    private final Duration idleTimeout;
    private final ZoneId timezone;

    /**
     * Creates a freshness evaluator with default settings:
     * daily reset at 4 AM, idle timeout of 2 hours, system timezone.
     */
    public SessionFreshnessEvaluator() {
        this(4, Duration.ofHours(2), ZoneId.systemDefault());
    }

    public SessionFreshnessEvaluator(int dailyResetHour, Duration idleTimeout, ZoneId timezone) {
        this.dailyResetHour = dailyResetHour;
        this.idleTimeout = idleTimeout;
        this.timezone = timezone;
    }

    /**
     * Determines if the session should be considered stale and reset.
     *
     * @param lastActivityAt the timestamp of the last activity in the session
     * @return true if the session should be reset
     */
    public boolean isStale(Instant lastActivityAt) {
        if (lastActivityAt == null) {
            return true;
        }

        Instant now = Instant.now();

        if (idleTimeout != null
                && Duration.between(lastActivityAt, now).compareTo(idleTimeout) > 0) {
            return true;
        }

        if (dailyResetHour >= 0) {
            ZonedDateTime lastActivity = lastActivityAt.atZone(timezone);
            ZonedDateTime nowZoned = now.atZone(timezone);

            LocalTime resetTime = LocalTime.of(dailyResetHour, 0);
            ZonedDateTime todayReset = nowZoned.toLocalDate().atTime(resetTime).atZone(timezone);

            if (nowZoned.isAfter(todayReset) && lastActivity.isBefore(todayReset)) {
                return true;
            }
        }

        return false;
    }

    public int getDailyResetHour() {
        return dailyResetHour;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }
}
