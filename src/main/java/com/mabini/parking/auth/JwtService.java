package com.mabini.parking.auth;

import com.mabini.parking.config.AppProperties;
import com.mabini.parking.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Creates and checks login tokens (JWT). */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration lifetime;
    /** Real time on purpose: the demo time machine must not expire anyone's login. */
    private final Clock clock = Clock.systemUTC();

    public JwtService(AppProperties props) {
        this.lifetime = Duration.ofMinutes(props.jwt().expirationMinutes());

        String secret = props.jwt().secret();
        byte[] keyBytes;
        if (secret == null || secret.isBlank()) {
            keyBytes = new byte[64];
            new SecureRandom().nextBytes(keyBytes);
            log.warn("app.jwt.secret is not set. Using a random secret: everyone is logged out whenever the app restarts.");
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("app.jwt.secret must be at least 32 characters long.");
            }
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    public long getLifetimeSeconds() {
        return lifetime.toSeconds();
    }

    /** Returns the username if the token is genuine and not expired, otherwise empty. */
    public Optional<String> extractUsername(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
