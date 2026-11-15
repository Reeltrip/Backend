package com.reeltrip.domain.auth.dto;

public record PasswordResetVerifyRequest(String token) {
    @Override public String toString() { return "PasswordResetVerifyRequest[REDACTED]"; }
}
