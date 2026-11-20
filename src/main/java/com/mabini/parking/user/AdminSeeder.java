package com.mabini.parking.user;

import com.mabini.parking.common.PasswordGenerator;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Creates the first super admin at startup if none exists, so the system can always be
 * bootstrapped. The password comes from config (never from Git). If none is configured,
 * a random one is generated and printed ONCE in the console. Either way the admin must
 * change it at first login.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.SUPER_ADMIN)) {
            resetIfRequested();
            return;
        }

        String username = props.seedAdmin().username().trim().toLowerCase();
        String password = props.seedAdmin().password();
        boolean generated = false;

        if (password == null || password.isBlank()) {
            password = PasswordGenerator.random(16);
            generated = true;
        } else if (password.length() < 8) {
            throw new IllegalStateException("app.seed-admin.password must be at least 8 characters.");
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setFullName(props.seedAdmin().fullName());
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.SUPER_ADMIN);
        admin.setActive(true);
        admin.setMustChangePassword(true);
        admin.setCreatedAt(LocalDateTime.now(clock));
        users.save(admin);

        if (generated) {
            log.warn("""

                    ==================================================================
                     First super admin created (shown ONCE, copy it now)
                       username: {}
                       password: {}
                     You must change the password at first login.
                    ==================================================================
                    """, username, password);
        } else {
            log.info("Super admin '{}' created from configuration. Password change required at first login.", username);
        }
    }

    /**
     * Forgotten super admin password. Only someone who can restart the app with settings (so, someone
     * with access to the machine) can do this: set SEED_ADMIN_RESET=true and SEED_ADMIN_PASSWORD.
     * The password is replaced, a change is required at next login, and any lockout is cleared.
     */
    private void resetIfRequested() {
        if (!props.seedAdmin().resetPassword()) {
            return;
        }
        String password = props.seedAdmin().password();
        if (password == null || password.length() < 8) {
            throw new IllegalStateException(
                    "app.seed-admin.reset-password is on, so app.seed-admin.password (at least 8 characters) is required.");
        }
        String username = props.seedAdmin().username().trim().toLowerCase();
        User admin = users.findByUsername(username)
                .filter(u -> u.getRole() == Role.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "No super admin named '" + username + "' exists, so there is nothing to reset."));
        admin.setPasswordHash(encoder.encode(password));
        admin.setMustChangePassword(true);
        admin.setActive(true);
        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);
        users.save(admin);
        log.warn("Super admin '{}' password was reset from configuration. Change it at first login, then remove "
                + "SEED_ADMIN_RESET and SEED_ADMIN_PASSWORD.", username);
    }
}
