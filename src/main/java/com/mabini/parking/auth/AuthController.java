package com.mabini.parking.auth;

import com.mabini.parking.auth.dto.ChangePasswordRequest;
import com.mabini.parking.auth.dto.LoginRequest;
import com.mabini.parking.auth.dto.LoginResponse;
import com.mabini.parking.auth.dto.RegisterRequest;
import com.mabini.parking.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public. Anyone can register; they always become a plain USER. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Public. Returns a token to send as "Authorization: Bearer <token>". */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Logged-in users change their own password. */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser current,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(current.id(), request);
    }
}
