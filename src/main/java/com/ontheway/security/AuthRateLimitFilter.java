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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
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
                && (path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request) + ':' + request.getRequestURI();
        long now = clock.millis();
        Bucket bucket = buckets.compute(key, (ignored, current) -> {
            if (current == null || now >= current.windowStartMillis + WINDOW_MILLIS) {
                return new Bucket(now, new AtomicInteger(1));
            }
            current.count.incrementAndGet();
            return current;
        });

        if (bucket.count.get() > maxRequestsPerMinute) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many authentication attempts. Try again shortly.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Bucket(long windowStartMillis, AtomicInteger count) {}
}
