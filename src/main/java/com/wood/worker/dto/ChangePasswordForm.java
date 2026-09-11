package com.wood.worker.dto;

public record ChangePasswordForm(
        String currentPassword,
        String newPassword) {
}