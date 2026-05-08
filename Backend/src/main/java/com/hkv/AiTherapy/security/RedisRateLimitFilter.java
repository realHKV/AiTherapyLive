package com.hkv.AiTherapy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RedisRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    // e.g. 20 requests per minute for AI chat endpoints
    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    public RedisRateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit chat session/message endpoints to protect AI cost
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/chat") && request.getMethod().equals("POST")) {
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
                String userId = (String) authentication.getPrincipal();
                
                String key = "rl:chat:" + userId;
                Long currentRequests = redisTemplate.opsForValue().increment(key);
                
                if (currentRequests != null) {
                    if (currentRequests == 1) {
                        redisTemplate.expire(key, Duration.ofMinutes(1)); // Start 1-minute fixed window
                    }
                    
                    if (currentRequests > MAX_REQUESTS_PER_MINUTE) {
                        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                        response.setContentType("application/json");
                        response.getWriter().write("""
                                {
                                    "success": false,
                                    "error": {
                                        "code": "RATE_LIMIT_EXCEEDED",
                                        "message": "You have exceeded the maximum number of AI chat requests per minute."
                                    }
                                }
                                """);
                        return; // Halt chain
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
