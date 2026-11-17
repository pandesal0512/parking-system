package com.mabini.parking.user;

import com.mabini.parking.common.enums.Role;
import com.mabini.parking.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
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
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

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
            return;
        }

        String username = props.seedAdmin().username().trim().toLowerCase();
        String password = props.seedAdmin().password();
        boolean generated = false;

        if (password == null || password.isBlank()) {
            password = randomPassword();
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

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
