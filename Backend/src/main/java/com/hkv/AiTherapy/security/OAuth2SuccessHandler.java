package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public OAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider,
                                UserRepository userRepository,
                                TherapyProfileRepository therapyProfileRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.therapyProfileRepository = therapyProfileRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String subject = oAuth2User.getAttribute("sub"); // Google provider's subject

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

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        System.out.println("=== GENERATED TOKEN ===");
        System.out.println(accessToken);
        System.out.println("=== END TOKEN ===");
        
        // Normally you'd also generate a refresh token and save it to DB here

        // Detect if this is a brand-new user (no therapy profile created yet)
        boolean isNewUser = !therapyProfileRepository.existsByUserId(user.getId());

        // Redirect back to frontend with token (and new_user flag for onboarding routing)
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString("http://localhost:5173/oauth2/redirect")
                .queryParam("token", accessToken);

        if (isNewUser) {
            builder.queryParam("new_user", "true");
        }

        String targetUrl = builder.build().encode().toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
