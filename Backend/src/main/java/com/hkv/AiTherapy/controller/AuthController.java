package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.request.LoginRequest;
import com.hkv.AiTherapy.dto.request.RegisterRequest;
import com.hkv.AiTherapy.dto.request.TokenRefreshRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.AuthResponse;
import com.hkv.AiTherapy.security.JwtTokenProvider;
import com.hkv.AiTherapy.service.account.AccountService;
import com.hkv.AiTherapy.service.auth.AuthService;
import com.hkv.AiTherapy.service.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountService accountService;

    public AuthController(AuthService authService, TokenService tokenService, JwtTokenProvider jwtTokenProvider, AccountService accountService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accountService = accountService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.register(request, userAgent);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("REGISTRATION_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.login(request, userAgent);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request,
                                                             HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.refresh(request.getRefreshToken(), userAgent);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("REFRESH_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(@RequestBody TokenRefreshRequest request,
                                                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // Revoke refresh token
            if (StringUtils.hasText(request.getRefreshToken())) {
                var tokenEntity = tokenService.validateAndGetRefreshToken(request.getRefreshToken());
                tokenService.revokeRefreshToken(tokenEntity);
            }

            // Denylist access token
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(jwt)) {
                    String jti = jwtTokenProvider.getJti(jwt);
                    tokenService.denylistAccessToken(jti);
                }
            }

            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Logged out successfully")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("LOGOUT_FAILED", e.getMessage()));
        }
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@AuthenticationPrincipal String userId) {
        try {
            accountService.deleteAccount(UUID.fromString(userId));
            return ResponseEntity.ok(ApiResponse.success("Account and all associated data have been permanently deleted."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("DELETE_FAILED", e.getMessage()));
        }
    }
}
