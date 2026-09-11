package com.wood.worker.service;

import com.wood.worker.model.AdminUser;
import com.wood.worker.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminusertest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.user=admin",
        "app.admin.password=test"
})
@Transactional
class AdminUserServiceTest {

    @Autowired
    private AdminUserService service;

    @Autowired
    private AdminUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    void seedIfEmptyCreatesConfiguredAdmin() {
        assertEquals(1, users.count());
        AdminUser admin = users.findByUsername("admin").orElseThrow();
        assertEquals("admin", admin.getUsername());
        assertTrue(encoder.matches("test", admin.getPasswordHash()));
        assertFalse(encoder.matches("wrong", admin.getPasswordHash()));
    }

    @Test
    void seedIfEmptyIsIdempotent() {
        service.seedIfEmpty("admin", "test");
        assertEquals(1, users.count());
    }

    @Test
    void changePasswordUpdatesStoredHash() {
        String username = service.changePassword("admin", "test", "newpass123");
        assertEquals("admin", username);
        AdminUser admin = users.findByUsername("admin").orElseThrow();
        assertTrue(encoder.matches("newpass123", admin.getPasswordHash()));
        assertFalse(encoder.matches("test", admin.getPasswordHash()));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "not-the-password", "newpass123"));
    }

    @Test
    void changePasswordRejectsNullCurrentPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", null, "newpass123"));
    }

    @Test
    void changePasswordRejectsShortNewPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "test", "short"));
    }

    @Test
    void changePasswordRejectsNullNewPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "test", null));
    }

    @Test
    void changePasswordRejectsUnknownUser() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("nobody", "anything", "newpass123"));
    }
}