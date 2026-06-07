package kapur.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate Limiter shields API from request floods
 * using token bucket algorithm
 */

@Component
public class RateLimiter {

    private final int capacity;
    private final long refillIntervalMillis;
    private int tokens;
    private long lastRefillTimestamp;

    public RateLimiter(@Value("${distrotasker.rate-limiter.capacity}") int capacity, @Value("${distrotasker.rate-limiter.refill-interval-ms}") long refillIntervalMillis) {
        this.capacity = capacity;
        this.refillIntervalMillis = refillIntervalMillis;
        this.tokens = capacity;  // start full
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    /*
     * Attempts to consume one token.
     * @return true if token was available, false if rate limited
     */
    public synchronized boolean tryConsume() {
        refill();

        if (tokens > 0) {
            tokens--;
            System.out.println("[LIMITER] TOKEN CONSUMED. (" + tokens + "/" + capacity+ ") REMAIN");
            return true;
        }else {
            System.out.println("[LIMITER] REQUEST REJECTED. BUCKET EMPTY (0/" + capacity + ") REMAIN");
            return false;
        }
    }

    /*
     * Checks if enough time has passed since last refill,
     * resets tokens to full capacity if yes.
     * Called internally by tryConsume() synchronously to avoid async conflicts (eg. fighting for last token).
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTimestamp;

        if (elapsed >= refillIntervalMillis) {
            tokens = capacity;
            lastRefillTimestamp = now;
            System.out.println("[LIMITER] BUCKET REFILLED. (" + tokens + "/" + capacity+ ") REMAIN");
        }
    }

    // Getters for testing/logging
    public synchronized int getAvailableTokens() {
        refill();
        return tokens;
    }
}