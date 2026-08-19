package com.woodfurni.security;

import com.woodfurni.auth.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token provider using JJWT 0.12.x.
 * Handles generation, validation, and parsing of access/refresh tokens.
 */
@Component
public class JwtProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:3600000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Generate an access token for a user.
     *
     * @param userId the user's ID
     * @param role   the user's role
     * @return the signed JWT access token
     */
    public String generateAccessToken(String userId, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate a refresh token for a user.
     *
     * @param userId the user's ID
     * @param role   the user's role
     * @return the signed JWT refresh token
     */
    public String generateRefreshToken(String userId, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validate a JWT token.
     *
     * @param token the token to validate
     * @return true if valid and not expired, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extract user ID from a JWT token.
     *
     * @param token the token
     * @return the user ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get(CLAIM_USER_ID, String.class);
    }

    /**
     * Extract role from a JWT token.
     *
     * @param token the token
     * @return the role
     */
    public Role getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        String roleName = claims.get(CLAIM_ROLE, String.class);
        return Role.valueOf(roleName);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get access token expiration time in milliseconds.
     */
    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    /**
     * Get refresh token expiration time in milliseconds.
     */
    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}
