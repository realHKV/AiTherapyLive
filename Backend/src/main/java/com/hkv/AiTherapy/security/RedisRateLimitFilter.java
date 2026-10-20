package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import com.hkv.AiTherapy.service.payment.EntitlementService;
import com.hkv.AiTherapy.service.usage.RedisAdmissionService;
import com.hkv.AiTherapy.service.usage.UsageException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/** Early atomic admission gate. Durable limits are reserved by UsageAccountingService. */
@Component
public class RedisRateLimitFilter extends OncePerRequestFilter {
    private final RedisAdmissionService admission;
    private final EntitlementService entitlements;

    public RedisRateLimitFilter(RedisAdmissionService admission, EntitlementService entitlements) {
        this.admission = admission;
        this.entitlements = entitlements;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isBillableChatSubmission(request)) { chain.doFilter(request, response); return; }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) { chain.doFilter(request, response); return; }
        UUID userId;
        try { userId = UUID.fromString((String) auth.getPrincipal()); }
        catch (IllegalArgumentException | ClassCastException ignored) { chain.doFilter(request, response); return; }
        try {
            admission.admit(userId, entitlements.isPro(userId) ? ResolvedGeneration.Tier.PRO : ResolvedGeneration.Tier.FREE);
        } catch (RedisAdmissionService.RateLimitedException e) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(e.retryAfterSeconds()));
            response.setHeader("X-Usage-Reset-At", Instant.now().plusSeconds(e.retryAfterSeconds()).toString());
            write(response, "RATE_LIMITED", "Too many AI requests. Try again later.");
            return;
        } catch (UsageException e) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            write(response, "ACCOUNTING_UNAVAILABLE", "Usage accounting is temporarily unavailable. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isBillableChatSubmission(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return false;
        String path = request.getRequestURI();
        return path.matches(".*/chat/sessions$") || path.matches(".*/chat/sessions/[0-9a-fA-F-]+/(messages|stream|refresh-profile)$");
    }
    private void write(HttpServletResponse response, String code, String message) throws IOException {
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
    }
}
