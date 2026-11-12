package com.hbsd.labselect.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-hours:12}") long expireHours) {
        // HS256 要求密钥不少于 32 字节，配置过短时补齐，避免启动期直接崩溃
        String normalized = secret == null ? "" : secret;
        while (normalized.getBytes(StandardCharsets.UTF_8).length < 32) {
            normalized = normalized + "-labselect";
        }
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String issue(Long userId, String role, String name, Long labId, Long tutorId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("name", name)
                .claim("labId", labId)
                .claim("tutorId", tutorId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(expireHours))))
                .signWith(key)
                .compact();
    }

    public UserContext.CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        String name = claims.get("name", String.class);
        Long labId = claims.get("labId", Number.class) == null ? null : claims.get("labId", Number.class).longValue();
        Long tutorId = claims.get("tutorId", Number.class) == null ? null : claims.get("tutorId", Number.class).longValue();
        return new UserContext.CurrentUser(userId, role, name, labId, tutorId);
    }
}
