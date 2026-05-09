package edu.iuh.fit.se.commonservice.service;

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window rate limiter.
 * Not distributed — resets on restart.  Good enough for single-instance anti-spam.
 */
@Service
public class RateLimitService {

    /** Key -> queue of timestamps (ms) for accepted requests */
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * Check whether the action is allowed for the given key.
     *
     * @param key        e.g. "report:userId" or "message:userId"
     * @param maxRequests max requests allowed in the window
     * @param windowMs    window size in milliseconds
     * @return true if allowed, false if rate-limited
     */
    public synchronized boolean isAllowed(String key, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = windows.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Remove timestamps outside the window
        while (!queue.isEmpty() && (now - queue.peekFirst()) > windowMs) {
            queue.pollFirst();
        }

        if (queue.size() < maxRequests) {
            queue.addLast(now);
            return true;
        }
        return false;
    }

    /** Convenience: max 5 reports per user per minute */
    public boolean allowReport(String userId) {
        return isAllowed("report:" + userId, 5, 60_000L);
    }

    /** Convenience: max 30 messages per user per minute */
    public boolean allowMessage(String userId) {
        return isAllowed("message:" + userId, 30, 60_000L);
    }

    /** Convenience: max 10 post submits per user per minute */
    public boolean allowPost(String userId) {
        return isAllowed("post:" + userId, 10, 60_000L);
    }
}
