package com.paymentplatform.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Manages payment idempotency using Redis.
 *
 * Flow:
 *   1. Before processing: check if key exists in Redis
 *      - EXISTS → return cached response (skip all processing)
 *      - NOT EXISTS → process payment, store result, return result
 *
 * Why Redis and not just the DB unique constraint?
 *   - Redis check is ~0.1ms. DB query is ~5-20ms.
 *   - Redis is the fast path. DB unique constraint is the safety net.
 *   - Under high load (duplicate burst), Redis absorbs the traffic
 *     before it hits PostgreSQL.
 *
 * TTL: 24 hours — matches industry standard (Stripe uses 24h too).
 * After 24h, the same key could be reused for a new payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "idempotency:payment:";
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Check if a payment with this idempotency key was already processed.
     *
     * @return Optional with the stored payment ID if already processed,
     *         empty Optional if this is a new request
     */
    public Optional<String> getStoredPaymentId(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        String storedPaymentId = redisTemplate.opsForValue().get(redisKey);

        if (storedPaymentId != null) {
            log.info("Idempotency hit — key: {}, existing paymentId: {}",
                    idempotencyKey, storedPaymentId);
            return Optional.of(storedPaymentId);
        }

        return Optional.empty();
    }

    /**
     * Store the result of a processed payment.
     * Called after successful payment creation.
     *
     * @param idempotencyKey the client-provided key
     * @param paymentId      the UUID of the created payment record
     */
    public void storePaymentId(String idempotencyKey, String paymentId) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, paymentId, TTL);
        log.info("Idempotency stored — key: {}, paymentId: {}, TTL: {}",
                idempotencyKey, paymentId, TTL);
    }

    /**
     * Check if key exists without retrieving value.
     * Useful for quick existence checks before full processing.
     */
    public boolean exists(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Boolean exists = redisTemplate.hasKey(redisKey);
        return Boolean.TRUE.equals(exists);
    }
}