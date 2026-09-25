package com.ebrahimmorkas.shortener.ratelimit;

import com.ebrahimmorkas.shortener.ratelimit.RateLimitProperties.Policy;
import com.ebrahimmorkas.shortener.ratelimit.RedisRateLimiter.Decision;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URI;

/**
 * Enforces {@link RateLimited} policies per client IP and reports the standard
 * {@code X-RateLimit-*} / {@code Retry-After} headers.
 *
 * <p>The client is identified by {@code request.getRemoteAddr()}. Behind a trusted reverse
 * proxy, enable {@code server.forward-headers-strategy} so that reflects {@code X-Forwarded-For};
 * never trust that header when clients can reach the app directly, or they could spoof it.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final RedisRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.enabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimited rateLimited = method.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true;
        }
        Policy policy = properties.policies().get(rateLimited.value());
        if (policy == null) {
            throw new IllegalStateException("No rate limit policy named " + rateLimited.value());
        }

        Decision decision = rateLimiter.tryAcquire(rateLimited.value(), policy, request.getRemoteAddr());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit of %d requests per %ds exceeded".formatted(policy.limit(), policy.window().toSeconds()));
        problem.setTitle("Too many requests");
        problem.setInstance(URI.create(request.getRequestURI()));
        objectMapper.writeValue(response.getOutputStream(), problem);
        return false;
    }
}
