package com.ontheway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_BUCKETS = 10_000;
    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong();
    private final Clock clock;
    private final int maxRequestsPerMinute;

    public AuthRateLimitFilter(Clock clock,
                               @Value("${ontheway.security.rate-limit.auth-requests-per-minute:10}")
                               int maxRequestsPerMinute) {
        this.clock = clock;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("POST".equalsIgnoreCase(request.getMethod())
                && RATE_LIMITED_PATHS.contains(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request) + ':' + request.getRequestURI();
        long now = clock.millis();
        evictExpiredBuckets(now);
        if (!buckets.containsKey(key) && buckets.size() >= MAX_TRACKED_BUCKETS) {
            writeRateLimitResponse(response);
            return;
        }
        Bucket bucket = buckets.compute(key, (ignored, current) -> {
            if (current == null || now >= current.windowStartMillis + WINDOW_MILLIS) {
                return new Bucket(now, new AtomicInteger(1));
            }
            current.count.incrementAndGet();
            return current;
        });

        if (bucket.count.get() > maxRequestsPerMinute) {
            writeRateLimitResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void evictExpiredBuckets(long now) {
        long previous = lastCleanupMillis.get();
        if (now - previous < WINDOW_MILLIS
                || !lastCleanupMillis.compareAndSet(previous, now)) {
            return;
        }
        buckets.entrySet().removeIf(entry ->
                now >= entry.getValue().windowStartMillis + WINDOW_MILLIS);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"message\":\"Too many authentication attempts. Try again shortly.\"}");
    }

    private record Bucket(long windowStartMillis, AtomicInteger count) {}
}
