package com.reeltrip.domain.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
    @Override
    public String toString() {
        return "TokenResponse[tokens=REDACTED]";
    }
}
