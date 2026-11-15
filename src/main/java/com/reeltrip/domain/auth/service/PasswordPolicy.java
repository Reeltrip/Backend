package com.reeltrip.domain.auth.service;

import java.nio.charset.StandardCharsets;

/** 가입과 재설정에서 같은 BCrypt 입력 규칙을 써요. */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static boolean isValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                && password.chars().anyMatch(c -> c >= '0' && c <= '9');
    }
}
