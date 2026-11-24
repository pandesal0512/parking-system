package com.mabini.parking.auth;

import com.mabini.parking.common.exception.ErrorResponseWriter;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
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
import java.util.List;
import java.util.Optional;

/**
 * Runs on every request. If there is a valid "Authorization: Bearer ..." token, it loads the
 * user from the database and marks the request as logged in.
 *
 * The user is re-checked in the database each time, so deactivating an account or changing a
 * role takes effect immediately, even for tokens that were already issued.
 *
 * NOTE: this class is created with "new" inside SecurityConfig on purpose. If it were a Spring
 * bean, Spring Boot would also register it as a plain servlet filter and it would run twice.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final ErrorResponseWriter errorWriter;

    public JwtAuthFilter(JwtService jwtService, UserRepository users, ErrorResponseWriter errorWriter) {
        this.jwtService = jwtService;
        this.users = users;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            Optional<String> username = jwtService.extractUsername(token);

            if (username.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<User> found = users.findByUsername(username.get());

                if (found.isPresent() && found.get().isActive()) {
                    User user = found.get();

                    // A temporary password must be changed before anything else is allowed.
                    if (user.isMustChangePassword() && !isAllowedWhileChangingPassword(request)) {
                        errorWriter.write(response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                                "You must change your password before continuing.", request.getRequestURI());
                        return;
                    }

                    AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.getRole());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isAllowedWhileChangingPassword(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/change-password") || path.equals("/api/users/me");
    }
}
