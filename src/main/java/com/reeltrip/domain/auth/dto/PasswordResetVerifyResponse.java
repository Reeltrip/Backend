package com.reeltrip.domain.auth.dto;

import java.time.OffsetDateTime;

public record PasswordResetVerifyResponse(String maskedEmail, OffsetDateTime expiresAt) { }
