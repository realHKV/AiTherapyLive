package com.hkv.AiTherapy.security;

import com.hkv.AiTherapy.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.private-key:}")
    private String privateKeyBase64;

    @Value("${jwt.public-key:}")
    private String publicKeyBase64;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpirySeconds;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() throws Exception {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank() ||
            publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new IllegalStateException("JWT RSA keys are not configured properly.");
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // Parse Private Key
        byte[] privBytes = Base64.getDecoder().decode(privateKeyBase64.replaceAll("\\s", ""));
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privBytes);
        this.privateKey = keyFactory.generatePrivate(privSpec);

        // Parse Public Key
        byte[] pubBytes = Base64.getDecoder().decode(publicKeyBase64.replaceAll("\\s", ""));
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubBytes);
        this.publicKey = keyFactory.generatePublic(pubSpec);
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (accessTokenExpirySeconds * 1000));
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .id(jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public boolean validateToken(String token) {
        System.out.println("Validate token callled");
        try {
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
            System.out.println("ok");
            return true;
        } catch (Exception e) {
            // Log exception here in real application
            System.out.println("JWT validation failed: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getJti(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getId();
    }

    public Authentication getAuthentication(String token) {
        String userId = getUserIdFromToken(token);
        // We use the userId as the principal. Optionally we could load the full user.
        return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
