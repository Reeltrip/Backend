package com.reeltrip.domain.auth.dto;

public record LoginResponse(Long userId, String nickname, String accessToken, String refreshToken) {
    @Override
    public String toString() {
        return "LoginResponse[userId=" + userId + ", tokens=REDACTED]";
    }
}
