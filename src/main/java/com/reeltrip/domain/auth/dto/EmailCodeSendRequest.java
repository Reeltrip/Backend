package com.reeltrip.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCodeSendRequest(@NotBlank @Email @Size(max = 255) String email) {
}
