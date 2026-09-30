package com.wood.worker.service;

import com.wood.worker.model.AdminUser;
import com.wood.worker.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    public static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * BCrypt silently ignores everything past 72 bytes, so a longer password would
     * authenticate against a truncated value. Reject it instead of pretending.
     */
    public static final int MAX_PASSWORD_BYTES = 72;
    private static final int GENERATED_PASSWORD_BYTES = 18;

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserService(AdminUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * Seeds the single admin account on an empty database. A blank password means
     * no committed default: a random one is generated and logged once, so a fresh
     * database never comes up with a public credential pair.
     */
    @Transactional
    public void seedIfEmpty(String username, String password) {
        if (users.count() > 0) {
            log.info("Admin user already present; skipping seed. ADMIN_PASSWORD and "
                    + "ADMIN_RESET_PASSWORD only act on an empty database or when explicitly set.");
            return;
        }
        boolean generated = password == null || password.isBlank();
        String effective = generated ? generatePassword() : requireLongEnough(password);
        users.save(new AdminUser(username, encoder.encode(effective)));
        if (generated) {
            log.info("Seeded initial admin user '{}' from ADMIN_PASSWORD. "
                    + "Change it from the Account tab.", username);
        } else {
            log.warn("Seeded initial admin user '{}' with a generated password because "
                    + "ADMIN_PASSWORD was not set. Generated password: {}\n"
                    + "Store it now — it is not recoverable and is never logged again. "
                    + "Change it from the Account tab.", username, effective);
        }
    }

    /**
     * Recovery path for a forgotten admin password: honours ADMIN_RESET_PASSWORD
     * only while it is explicitly set, so an env var left behind cannot quietly
     * re-apply an old password on the next deploy. Clear the variable once the
     * deploy is done.
     */
    @Transactional
    public void resetPasswordIfRequested(String username, String requestedPassword) {
        if (requestedPassword == null || requestedPassword.isBlank()) {
            return;
        }
        String password = requireLongEnough(requestedPassword);
        AdminUser user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN_RESET_PASSWORD is set but no admin user '" + username
                                + "' exists. Unset ADMIN_RESET_PASSWORD once the admin exists."));
        user.setPasswordHash(encoder.encode(password));
        users.save(user);
        log.warn("Admin password for '{}' was overwritten from ADMIN_RESET_PASSWORD. "
                + "Unset ADMIN_RESET_PASSWORD now, otherwise the next deploy resets it again.", username);
    }

    @Transactional
    public String changePassword(String username, String currentPassword, String newPassword) {
        AdminUser user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown admin user"));
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (utf8Length(newPassword) > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("New password must be at most "
                    + MAX_PASSWORD_BYTES + " bytes");
        }
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        log.info("Admin password for '{}' changed through the admin account panel.", username);
        return user.getUsername();
    }

    private String requireLongEnough(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD / ADMIN_RESET_PASSWORD must be at least "
                    + MIN_PASSWORD_LENGTH + " characters");
        }
        if (utf8Length(password) > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("ADMIN_PASSWORD / ADMIN_RESET_PASSWORD must be at most "
                    + MAX_PASSWORD_BYTES + " bytes (BCrypt ignores the rest)");
        }
        return password;
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String generatePassword() {
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
