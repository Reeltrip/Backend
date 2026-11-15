package com.reeltrip.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** S0-1b 가입. 필수 약관 2개(이용약관·개인정보)는 true여야 하고, 마케팅 동의는 선택이에요. */
public record SignupRequest(@NotBlank @Email @Size(max = 255) String email,
                            @NotBlank @Size(max = 256) String password,
                            @NotBlank @Size(max = 8) String nickname,
                            @NotNull @AssertTrue Boolean isTermsAgreed,
                            @NotNull @AssertTrue Boolean isPrivacyAgreed,
                            Boolean isMarketingAgreed) {
    @Override
    public String toString() {
        return "SignupRequest[credentials=REDACTED]";
    }
}
