package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.auth.OAuthTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final TherapyProfileRepository therapyProfileRepository;
    private final OAuthTicketService ticketService;

    @Value("${app.auth.oauth-ticket-handoff:false}")
    private boolean ticketHandoff;

    // First origin in CORS_ALLOWED_ORIGINS is treated as the frontend base URL
    // e.g. "https://your-app.netlify.app" in production, "http://localhost:5173" locally
    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Autowired
    public OAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider,
                                UserRepository userRepository,
                                TherapyProfileRepository therapyProfileRepository,
                                OAuthTicketService ticketService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.therapyProfileRepository = therapyProfileRepository;
        this.ticketService = ticketService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String subject = oAuth2User.getAttribute("sub"); // Google provider's subject

        if (email == null || email.isBlank() || subject == null || subject.isBlank()
                || !Boolean.TRUE.equals(oAuth2User.getAttribute("email_verified"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google identity is incomplete");
            return;
        }

        // Find or create user
        User user = userRepository.findByOauthProviderAndOauthSubject("google", subject)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .orElseGet(() -> {
                            User newUser = new User();
                            newUser.setEmail(email);
                            newUser.setDisplayName(name);
                            newUser.setOauthProvider("google");
                            newUser.setOauthSubject(subject);
                            newUser.setVerified(true);
                            return userRepository.save(newUser);
                        }));

        if (!user.isActive() || (user.getOauthSubject() != null && !subject.equals(user.getOauthSubject()))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account unavailable");
            return;
        }
        if (user.getOauthSubject() == null) {
            user.setOauthProvider("google");
            user.setOauthSubject(subject);
            user.setVerified(true);
            user = userRepository.save(user);
        }

        // Detect if this is a brand-new user (no therapy profile created yet)
        boolean isNewUser = !therapyProfileRepository.existsByUserId(user.getId());

        // Redirect back to frontend with token (and new_user flag for onboarding routing)
        // Use the first allowed origin as the frontend base URL
        String frontendBaseUrl = corsAllowedOrigins.split(",")[0].trim();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendBaseUrl + "/oauth2/redirect");
        if (ticketHandoff) {
            builder.queryParam("ticket", ticketService.issue(user.getId()));
        } else {
            builder.queryParam("token", jwtTokenProvider.generateAccessToken(user));
        }

        if (isNewUser) {
            builder.queryParam("new_user", "true");
        }

        String targetUrl = builder.build().encode().toUriString();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
