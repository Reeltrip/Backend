package com.reeltrip.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/** 사용자에게는 무작위 원문을 주고, DB 조회에는 해시만 사용해요. */
@Component
public class RefreshTokenCodec {

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isValidFormat(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    public String hash(String token) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256을 사용할 수 없어요.", error);
        }
    }
}
