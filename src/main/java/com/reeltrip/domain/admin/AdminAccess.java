package com.reeltrip.domain.admin;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 관리자와 초기화 대상은 서버 설정으로만 지정해요. 빈 설정은 아무도 허용하지 않아요. */
@Component
public class AdminAccess {
    private final Set<Long> admins;
    private final Set<Long> demos;
    private final UserRepository users;

    public AdminAccess(@Value("${ADMIN_USER_IDS:}") String admins,
                       @Value("${DEMO_USER_IDS:}") String demos, UserRepository users) {
        this.admins = parse(admins);
        this.demos = parse(demos);
        this.users = users;
    }

    public AuthorizationDecision decision(Authentication auth) {
        return new AuthorizationDecision(auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof Long id && allowed(id));
    }

    public void requireAdmin(Long id) {
        if (id == null || !allowed(id)) { throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN); }
    }

    public boolean isDemo(Long id) { return demos.contains(id); }

    public void requireDemo(Long id) {
        if (!isDemo(id)) { throw new BusinessException(ErrorCode.ADMIN_DEMO_ONLY); }
    }

    private boolean allowed(Long id) { return admins.contains(id) && users.existsById(id); }

    private static Set<Long> parse(String value) {
        if (value.isBlank()) { return Set.of(); }
        try {
            return Arrays.stream(value.split(",", -1)).map(String::strip).map(Long::valueOf)
                    .peek(id -> { if (id <= 0) { throw new IllegalArgumentException(); } })
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ADMIN_USER_IDS·DEMO_USER_IDS는 양의 회원 ID를 쉼표로 구분해요.");
        }
    }
}
