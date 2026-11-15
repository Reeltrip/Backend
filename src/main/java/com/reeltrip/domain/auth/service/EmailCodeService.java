package com.reeltrip.domain.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.auth.dto.EmailCodeSendResponse;
import com.reeltrip.domain.auth.dto.EmailCodeVerifyResponse;
import com.reeltrip.domain.auth.entity.EmailCode;
import com.reeltrip.domain.auth.repository.EmailCodeRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.mail.EmailSender;

/** S0-1b 인증번호 받기·확인. 상태는 email_code 테이블에만 둬요(인스턴스가 여러 개여도 같은 결과). */
@Service
public class EmailCodeService {

    private static final Duration CODE_VALID_FOR = Duration.ofMinutes(5);
    private static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    private static final Duration SIGNUP_WITHIN = Duration.ofMinutes(30);

    private final EmailCodeRepository emailCodes;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(EmailCodeRepository emailCodes, UserRepository users, EmailSender emailSender, Clock clock) {
        this.emailCodes = emailCodes;
        this.users = users;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    /** 새 번호를 저장하고 메일로 보내요. 발송이 실패하면 저장도 되돌려요. 이전 번호는 가장 최근 번호만 보므로 자동으로 무효예요. */
    @Transactional
    public EmailCodeSendResponse sendCode(String email) {
        if (users.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        emailCodes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(email)
                .filter(latest -> !latest.canResend(now))
                .ifPresent(latest -> { throw new BusinessException(ErrorCode.EMAIL_CODE_TOO_FREQUENT); });
        String code = "%06d".formatted(random.nextInt(1_000_000));
        EmailCode saved = emailCodes.save(EmailCode.create(email, code, now));
        emailSender.sendSignupCode(email, code, CODE_VALID_FOR);
        return new EmailCodeSendResponse(toSeoul(saved.getExpiresAt()), toSeoul(now.plus(RESEND_AFTER)));
    }

    /** 틀린 횟수는 실패 응답을 보내도 남아야 해서 BusinessException에서 되돌리지 않아요. */
    @Transactional(noRollbackFor = BusinessException.class)
    public EmailCodeVerifyResponse verifyCode(String email, String submittedCode) {
        EmailCode code = findLatestForUpdate(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_EMAIL_CODE));
        LocalDateTime now = LocalDateTime.now(clock);
        if (code.isAttemptsExceeded()) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }
        if (code.isExpired(now)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!code.matches(submittedCode)) {
            code.recordFailedAttempt();
            throw new BusinessException(ErrorCode.INVALID_EMAIL_CODE);
        }
        code.markVerified(now);
        return new EmailCodeVerifyResponse(email, toSeoul(code.getCreatedAt().plus(SIGNUP_WITHIN)));
    }

    /** 가입 트랜잭션 안에서 불러요. 인증을 마쳤고 발송 30분 안이어야 해요. */
    @Transactional
    public void requireVerified(String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean verified = findLatestForUpdate(email).map(code -> code.canSignUp(now)).orElse(false);
        if (!verified) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    @Transactional
    public void removeCodes(String email) {
        emailCodes.deleteAllByEmail(email);
    }

    private Optional<EmailCode> findLatestForUpdate(String email) {
        return emailCodes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(email)
                .flatMap(latest -> emailCodes.findByEmailCodeIdForUpdate(latest.getEmailCodeId()));
    }

    private static OffsetDateTime toSeoul(LocalDateTime value) {
        return value.atZone(TimeConfig.SEOUL).toOffsetDateTime();
    }
}
