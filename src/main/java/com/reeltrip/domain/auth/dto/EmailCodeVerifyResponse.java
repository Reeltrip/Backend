package com.reeltrip.domain.auth.dto;

import java.time.OffsetDateTime;

public record EmailCodeVerifyResponse(String email, OffsetDateTime signupAvailableUntil) {
}
