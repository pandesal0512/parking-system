package com.mabini.parking.auth;

import com.mabini.parking.auth.dto.ChangePasswordRequest;
import com.mabini.parking.auth.dto.LoginRequest;
import com.mabini.parking.auth.dto.LoginResponse;
import com.mabini.parking.auth.dto.RegisterRequest;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.config.AppProperties;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import com.mabini.parking.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Invalid username or password.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AppProperties props;
    private final Clock clock;

    /** Used to spend the same time on unknown usernames, so response time doesn't reveal who exists. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService,
                       AppProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken.");
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setUserType(request.userType());
        user.setSchoolId(blankToNull(request.schoolId()));
        user.setRole(Role.USER); // always. The role never comes from the request.
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now(clock));

        try {
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two people registering the same username at the same moment.
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken.");
        }
    }

    /**
     * noRollbackFor is important: when a login fails we throw an error AND must still save the
     * failed-attempt counter. Without it, the exception would roll the counter back.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request) {
        String username = normalize(request.username());
        LocalDateTime now = LocalDateTime.now(clock);

        User user = users.findByUsername(username).orElse(null);
        if (user == null) {
            encoder.matches(request.password(), dummyHash);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_LOGIN);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw ApiException.tooManyRequests("ACCOUNT_LOCKED",
                    "Too many failed attempts. Please try again later.");
        }

        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= props.lockout().maxFailedAttempts()) {
                user.setLockedUntil(now.plusMinutes(props.lockout().lockMinutes()));
                attempts = 0;
            }
            user.setFailedLoginAttempts(attempts);
            users.save(user);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_LOGIN);
        }

        if (!user.isActive()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account has been deactivated.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);

        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getLifetimeSeconds(),
                user.getRole(),
                user.isMustChangePassword());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required."));

        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Your current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "The new password must be different.");
        }

        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        users.save(user);
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
