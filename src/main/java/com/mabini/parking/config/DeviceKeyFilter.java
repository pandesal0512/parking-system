package com.mabini.parking.config;

import com.mabini.parking.common.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets the gate hardware in. It only looks at URLs that start with /api/device/, and there it
 * requires the header "X-Device-Key" to match app.device.api-key.
 *
 *  - No key configured on the server: the device endpoints are OFF (403).
 *  - Missing or wrong key: 401.
 *  - Right key: the request is treated as the role DEVICE, which can use /api/device/** and nothing else.
 *
 * Created with "new" in SecurityConfig (same reason as JwtAuthFilter: avoid running twice).
 */
public class DeviceKeyFilter extends OncePerRequestFilter {

    private final AppProperties props;
    private final ErrorResponseWriter errorWriter;

    public DeviceKeyFilter(AppProperties props, ErrorResponseWriter errorWriter) {
        this.props = props;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/device/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Whatever a person's token said, a device URL is answered only for the device key.
        SecurityContextHolder.clearContext();

        String configured = props.device().apiKey();
        if (configured == null || configured.isBlank()) {
            errorWriter.write(response, HttpStatus.FORBIDDEN, "DEVICE_API_DISABLED",
                    "The gate hardware endpoints are switched off. Set DEVICE_API_KEY on the server.",
                    request.getRequestURI());
            return;
        }

        String sent = request.getHeader("X-Device-Key");
        if (sent == null || !sameSecret(configured, sent)) {
            errorWriter.write(response, HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_KEY",
                    "The device key is missing or wrong.", request.getRequestURI());
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "gate-device", null, List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    /** Compares in constant time, so the time taken does not hint at how much of the key was right. */
    private static boolean sameSecret(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
