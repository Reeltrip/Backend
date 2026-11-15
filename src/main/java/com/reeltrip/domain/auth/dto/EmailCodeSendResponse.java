package com.reeltrip.domain.auth.dto;

import java.time.OffsetDateTime;

public record EmailCodeSendResponse(OffsetDateTime expiresAt, OffsetDateTime resendAvailableAt) {
}
