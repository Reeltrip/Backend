package com.reeltrip.domain.auth.dto;

public record PasswordResetConfirmRequest(String token, String newPassword) {
    @Override public String toString() { return "PasswordResetConfirmRequest[REDACTED]"; }
}
