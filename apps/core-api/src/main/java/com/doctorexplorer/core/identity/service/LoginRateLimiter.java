package com.doctorexplorer.core.identity.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas de login por conta e por IP, no Valkey (INCR + EXPIRE). Estourou: 429 com
 * Retry-After. A contagem da conta zera no login bem-sucedido; a do IP, não.
 */
@Component
public class LoginRateLimiter {

    public static final int MAX_PER_ACCOUNT = 10;
    public static final int MAX_PER_IP = 50;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision OK = new Decision(true, 0);
    }

    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Decision check(String accountKey, String ipHash) {
        Decision a = over("rl:login:acct:" + accountKey, MAX_PER_ACCOUNT);
        if (!a.allowed()) return a;
        return over("rl:login:ip:" + ipHash, MAX_PER_IP);
    }

    public void recordFailure(String accountKey, String ipHash) {
        bump("rl:login:acct:" + accountKey);
        bump("rl:login:ip:" + ipHash);
    }

    public void recordSuccess(String accountKey) {
        redis.delete("rl:login:acct:" + accountKey);
    }

    private Decision over(String key, int max) {
        String v = redis.opsForValue().get(key);
        long count = v == null ? 0 : Long.parseLong(v);
        if (count < max) return Decision.OK;
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return new Decision(false, ttl == null || ttl < 1 ? WINDOW.toSeconds() : ttl);
    }

    private void bump(String key) {
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) redis.expire(key, WINDOW);
    }
}
