package com.hkv.AiTherapy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @Autowired
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, StringRedisTemplate redisTemplate,
                                   UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                
                String jti = jwtTokenProvider.getJti(jwt);
                
                // Check if token is in Redis deny-list (logout feature)
                Boolean isDenylisted = redisTemplate.hasKey("jwt:denylist:" + jti);
                if (isDenylisted == null || isDenylisted) {
                    throw new SecurityException("Token is revoked.");
                }

                Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
                java.util.UUID userId = java.util.UUID.fromString(authentication.getName());
                User user = userRepository.findById(userId)
                        .filter(User::isActive)
                        .orElseThrow(() -> new SecurityException("Account is unavailable."));
                if (ReviewerAccount.isReviewer(user)) {
                    authentication = new UsernamePasswordAuthenticationToken(userId.toString(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
                }
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
