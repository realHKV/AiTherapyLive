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
import com.hkv.AiTherapy.service.auth.OAuthTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
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
    private final OAuthTicketService oauthTicketService;

    public AuthController(AuthService authService, TokenService tokenService, JwtTokenProvider jwtTokenProvider,
                          AccountService accountService, OAuthTicketService oauthTicketService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accountService = accountService;
        this.oauthTicketService = oauthTicketService;
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeOauthTicket(@Valid @RequestBody OAuthExchangeRequest request,
                                                                          HttpServletRequest httpRequest) {
        try {
            AuthResponse result = oauthTicketService.exchange(request.ticket(), httpRequest.getHeader("User-Agent"));
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(ApiResponse.success(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("OAUTH_TICKET_INVALID", "Sign-in link expired or already used."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("OAUTH_EXCHANGE_UNAVAILABLE", "Sign-in is temporarily unavailable."));
        }
    }

    public record OAuthExchangeRequest(@jakarta.validation.constraints.NotBlank String ticket) {}

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.register(request, userAgent);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("REGISTRATION_FAILED", "Registration failed. Check your details and try again."));
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
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid credentials."));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request,
                                                             HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.refresh(request.getRefreshToken(), userAgent);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("AUTH_UNAVAILABLE", "Session service is temporarily unavailable."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("REFRESH_FAILED", "Session could not be refreshed."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(@RequestBody TokenRefreshRequest request,
                                                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String jwt = StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7) : null;
            tokenService.logout(request.getRefreshToken(), jwt, jwtTokenProvider);

            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Logged out successfully")));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("AUTH_UNAVAILABLE", "Session service is temporarily unavailable."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("LOGOUT_FAILED", "Logout failed. Please try again."));
        }
    }

    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@AuthenticationPrincipal String userId) {
        try {
            accountService.deleteAccount(UUID.fromString(userId));
            return ResponseEntity.ok(ApiResponse.success("Account and all associated data have been permanently deleted."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("DELETE_FAILED", "Account deletion failed. Please try again."));
        }
    }
}
