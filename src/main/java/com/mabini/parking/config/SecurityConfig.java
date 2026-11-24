package com.mabini.parking.config;

import com.mabini.parking.auth.JwtAuthFilter;
import com.mabini.parking.auth.JwtService;
import com.mabini.parking.common.exception.ErrorResponseWriter;
import com.mabini.parking.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The security rules:
 *  - register and login are public, everything else needs a valid token
 *  - no sessions, no cookies (so CSRF protection is not needed)
 *  - role checks on individual endpoints use @PreAuthorize (enabled below)
 *  - the gate hardware uses a device key header instead of a login (only /api/device/**)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserRepository users,
                                                   AppProperties props,
                                                   ErrorResponseWriter errorWriter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: the login/register endpoints and the static front-end files.
                        // The front-end files contain no data, and every /api call still needs a token.
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/error").permitAll()
                        .requestMatchers("/", "/index.html", "/app.html", "/favicon.ico",
                                "/css/**", "/js/**").permitAll()
                        // The gate hardware: only with the device key (see DeviceKeyFilter).
                        .requestMatchers("/api/device/**").hasRole("DEVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> errorWriter.write(res,
                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                "Authentication is required.", req.getRequestURI()))
                        .accessDeniedHandler((req, res, e) -> errorWriter.write(res,
                                HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "You do not have permission to do this.", req.getRequestURI())))
                .addFilterBefore(new JwtAuthFilter(jwtService, users, errorWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new DeviceKeyFilter(props, errorWriter),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
