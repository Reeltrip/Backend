package com.reeltrip.domain.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.reeltrip.domain.auth.dto.PasswordResetVerifyResponse;
import com.reeltrip.domain.auth.entity.PasswordResetToken;
import com.reeltrip.domain.auth.repository.PasswordResetTokenRepository;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.mail.EmailSender;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository resets;
    private final RefreshTokenRepository refreshes;
    private final RefreshTokenCodec codec;
    private final PasswordEncoder encoder;
    private final EmailSender mail;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository resets,
            RefreshTokenRepository refreshes, RefreshTokenCodec codec, PasswordEncoder encoder, EmailSender mail, Clock clock) {
        this.users = users; this.resets = resets; this.refreshes = refreshes;
        this.codec = codec; this.encoder = encoder; this.mail = mail; this.clock = clock;
    }

    /** Dispatcher의 별도 스레드에서 실행해 가입 여부·SMTP 시간 차이가 HTTP 응답에 드러나지 않게 해요. */
    @Transactional
    public void request(String email) {
        var user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null) { return; }
        LocalDateTime now = LocalDateTime.now(clock);
        var latest = resets.findFirstByUserUserIdOrderByCreatedAtDescPasswordResetTokenIdDesc(user.getUserId());
        if (latest.isPresent() && now.isBefore(latest.get().getCreatedAt().plusSeconds(60))) { return; }
        String raw = codec.generate();
        resets.deleteAllByUserUserId(user.getUserId());
        resets.saveAndFlush(PasswordResetToken.create(user, raw, now));
        // SMTP가 실패하면 이전 링크 폐기와 새 토큰 저장을 함께 되돌려요.
        mail.sendPasswordReset(user.getEmail(), raw, Duration.ofMinutes(30));
    }

    @Transactional(readOnly = true)
    public PasswordResetVerifyResponse verify(String raw) {
        var reset = resets.findByTokenHash(hash(raw)).orElseThrow(() -> invalid());
        validate(reset, LocalDateTime.now(clock));
        String email = reset.getUser().getEmail();
        int at = email.lastIndexOf('@');
        int visible = email.offsetByCodePoints(0, Math.min(2, email.codePointCount(0, at)));
        return new PasswordResetVerifyResponse(email.substring(0, visible) + "*****" + email.substring(at),
                reset.getExpiresAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
    }

    @Transactional
    public void confirm(String raw, String newPassword) {
        String hash = hash(raw);
        if (!PasswordPolicy.isValid(newPassword)) { throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT); }
        Long userId = resets.userIdByHash(hash).orElseThrow(() -> invalid());
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> invalid());
        var reset = resets.findByTokenHashForUpdate(hash).orElseThrow(() -> invalid());
        LocalDateTime now = LocalDateTime.now(clock);
        validate(reset, now);
        user.changePassword(encoder.encode(newPassword));
        reset.markUsed(now);
        refreshes.deleteAllByUserUserId(userId);
    }

    private String hash(String raw) {
        if (!codec.isValidFormat(raw)) { throw invalid(); }
        return PasswordResetToken.hashToken(raw);
    }

    private static void validate(PasswordResetToken reset, LocalDateTime now) {
        if (reset.isUsed()) { throw invalid(); }
        if (reset.isExpired(now)) { throw new BusinessException(ErrorCode.RESET_TOKEN_EXPIRED); }
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.INVALID_RESET_TOKEN); }
}
