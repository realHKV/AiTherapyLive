package com.hkv.AiTherapy.service.auth;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.LoginRequest;
import com.hkv.AiTherapy.dto.request.RegisterRequest;
import com.hkv.AiTherapy.dto.response.AuthResponse;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpirySeconds;

    public AuthService(UserRepository userRepository, 
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider, 
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String deviceInfo) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .isVerified(false)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        return buildAuthResponse(user, deviceInfo);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new RuntimeException("Account is disabled");
        }

        return buildAuthResponse(user, deviceInfo);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String deviceInfo) {
        var tokenEntity = tokenService.validateAndGetRefreshToken(rawRefreshToken);
        
        // Rotate token (revoke old, issue new)
        tokenService.revokeRefreshToken(tokenEntity);
        
        User user = tokenEntity.getUser();
        if (!user.isActive()) {
            throw new RuntimeException("Account is disabled");
        }

        return buildAuthResponse(user, deviceInfo);
    }

    private AuthResponse buildAuthResponse(User user, String deviceInfo) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = tokenService.createRefreshToken(user, deviceInfo);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(accessTokenExpirySeconds)
                .user(new AuthResponse.UserDto(user.getId().toString(), user.getEmail()))
                .build();
    }
}
