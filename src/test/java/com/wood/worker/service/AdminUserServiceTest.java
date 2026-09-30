package com.wood.worker.service;

import com.wood.worker.model.AdminUser;
import com.wood.worker.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminusertest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.admin.user=admin",
        "app.admin.password=test-password"
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
        assertTrue(encoder.matches("test-password", admin.getPasswordHash()));
        assertFalse(encoder.matches("wrong", admin.getPasswordHash()));
    }

    @Test
    void seedIfEmptyIsIdempotent() {
        service.seedIfEmpty("admin", "test-password");
        assertEquals(1, users.count());
    }

    @Test
    void changePasswordUpdatesStoredHash() {
        String username = service.changePassword("admin", "test-password", "newpass123");
        assertEquals("admin", username);
        AdminUser admin = users.findByUsername("admin").orElseThrow();
        assertTrue(encoder.matches("newpass123", admin.getPasswordHash()));
        assertFalse(encoder.matches("test-password", admin.getPasswordHash()));
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
                () -> service.changePassword("admin", "test-password", "short"));
    }

    @Test
    void changePasswordRejectsNullNewPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "test-password", null));
    }

    @Test
    void changePasswordRejectsUnknownUser() {
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("nobody", "anything", "newpass123"));
    }

    @Test
    void seedWithoutAPasswordGeneratesOneThatIsNotABlankDefault() {
        AdminUserRepository repo = mockRepo();
        var service = new AdminUserService(repo, encoder);

        service.seedIfEmpty("admin", "   ");

        AdminUser seeded = captured(repo);
        assertEquals("admin", seeded.getUsername());
        assertTrue(seeded.getPasswordHash().startsWith("$2"), "password must be hashed");
        assertFalse(encoder.matches("", seeded.getPasswordHash()));
        assertFalse(encoder.matches("   ", seeded.getPasswordHash()));
    }

    @Test
    void seedWithNullPasswordAlsoGeneratesOne() {
        AdminUserRepository repo = mockRepo();

        new AdminUserService(repo, encoder).seedIfEmpty("admin", null);

        assertTrue(captured(repo).getPasswordHash().startsWith("$2"));
    }

    @Test
    void eachGeneratedPasswordIsDistinct() {
        AdminUserRepository first = mockRepo();
        AdminUserRepository second = mockRepo();

        new AdminUserService(first, encoder).seedIfEmpty("admin", null);
        new AdminUserService(second, encoder).seedIfEmpty("admin", null);

        assertNotEquals(captured(first).getPasswordHash(), captured(second).getPasswordHash(),
                "generated passwords must not repeat");
    }

    @Test
    void seedRejectsAConfiguredPasswordBelowTheMinimumLength() {
        AdminUserRepository repo = mockRepo();

        var error = assertThrows(IllegalStateException.class,
                () -> new AdminUserService(repo, encoder).seedIfEmpty("admin", "short"));

        assertTrue(error.getMessage().contains("at least 8 characters"), error.getMessage());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void seedLeavesAnExistingAdminUntouched() {
        service.seedIfEmpty("admin", "totally-different");

        assertTrue(encoder.matches("test-password", users.findByUsername("admin").orElseThrow().getPasswordHash()));
    }

    @Test
    void resetPasswordOverwritesTheStoredHash() {
        service.resetPasswordIfRequested("admin", "recovered123");

        AdminUser admin = users.findByUsername("admin").orElseThrow();
        assertTrue(encoder.matches("recovered123", admin.getPasswordHash()));
        assertFalse(encoder.matches("test-password", admin.getPasswordHash()));
    }

    @Test
    void resetPasswordIsANoOpWhenUnset() {
        service.resetPasswordIfRequested("admin", null);
        service.resetPasswordIfRequested("admin", "");
        service.resetPasswordIfRequested("admin", "   ");

        assertTrue(encoder.matches("test-password", users.findByUsername("admin").orElseThrow().getPasswordHash()));
    }

    @Test
    void resetPasswordRejectsAValueBelowTheMinimumLength() {
        assertThrows(IllegalStateException.class, () -> service.resetPasswordIfRequested("admin", "short"));
        assertTrue(encoder.matches("test-password", users.findByUsername("admin").orElseThrow().getPasswordHash()));
    }

    @Test
    void resetPasswordFailsLoudlyWhenTheAdminDoesNotExist() {
        var error = assertThrows(IllegalStateException.class,
                () -> service.resetPasswordIfRequested("nobody", "recovered123"));

        assertTrue(error.getMessage().contains("Unset ADMIN_RESET_PASSWORD"), error.getMessage());
    }

    @Test
    void changePasswordRejectsAnythingBcryptWouldTruncate() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> service.changePassword("admin", "test-password", "a".repeat(73)));

        assertTrue(error.getMessage().contains("at most 72 bytes"), error.getMessage());
    }

    @Test
    void changePasswordAcceptsExactlyTheBcryptLimit() {
        assertEquals("admin", service.changePassword("admin", "test-password", "a".repeat(72)));
    }

    @Test
    void seedRejectsAPasswordBcryptWouldTruncate() {
        AdminUserRepository repo = mockRepo();

        var error = assertThrows(IllegalStateException.class,
                () -> new AdminUserService(repo, encoder).seedIfEmpty("admin", "a".repeat(73)));

        assertTrue(error.getMessage().contains("at most 72 bytes"), error.getMessage());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void multiBytePasswordsAreMeasuredInBytes() {
        // 30 four-byte characters = 120 bytes, even though it is only 30 chars long.
        var error = assertThrows(IllegalStateException.class,
                () -> new AdminUserService(mockRepo(), encoder).seedIfEmpty("admin", "\uD83D\uDE00".repeat(30)));

        assertTrue(error.getMessage().contains("at most 72 bytes"), error.getMessage());
    }

    private static AdminUserRepository mockRepo() {
        return Mockito.mock(AdminUserRepository.class);
    }

    private static AdminUser captured(AdminUserRepository repo) {
        var captor = ArgumentCaptor.forClass(AdminUser.class);
        Mockito.verify(repo).save(captor.capture());
        return captor.getValue();
    }
}
