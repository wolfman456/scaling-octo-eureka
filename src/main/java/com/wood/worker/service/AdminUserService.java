package com.wood.worker.service;

import com.wood.worker.model.AdminUser;
import com.wood.worker.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;

    public AdminUserService(AdminUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void seedIfEmpty(String username, String password) {
        if (users.count() > 0) {
            return;
        }
        users.save(new AdminUser(username, encoder.encode(password)));
    }

    @Transactional
    public String changePassword(String username, String currentPassword, String newPassword) {
        AdminUser user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown admin user"));
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "New password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        return user.getUsername();
    }
}