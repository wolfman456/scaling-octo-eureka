package com.wood.worker.controller;

import com.wood.worker.dto.ChangePasswordForm;
import com.wood.worker.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminUserService users;

    public AdminAuthController(AdminUserService users) {
        this.users = users;
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordForm form,
                                                              Authentication authentication) {
        try {
            String username = users.changePassword(
                    authentication.getName(), form.currentPassword(), form.newPassword());
            return ResponseEntity.ok(Map.of("username", username));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}