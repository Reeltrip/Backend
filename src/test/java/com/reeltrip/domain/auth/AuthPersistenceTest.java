package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.reeltrip.domain.auth.entity.EmailCode;
import com.reeltrip.domain.auth.entity.PasswordResetToken;
import com.reeltrip.domain.auth.entity.RefreshToken;
import com.reeltrip.domain.auth.repository.EmailCodeRepository;
import com.reeltrip.domain.auth.repository.PasswordResetTokenRepository;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.config.JpaConfig;
import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.support.TestFixtures;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:reeltrip_auth_persistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,VALUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, TimeConfig.class})
class AuthPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Autowired EmailCodeRepository codes;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired RefreshTokenRepository refreshes;
    @Autowired UserRepository users;
    @Autowired TestEntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 시각에 재발송한 경우에도 마지막 번호가 최신 인증번호로 조회된다.")
    void latestCodeSurvivesReload() {
        // given
        codes.saveAndFlush(EmailCode.create("demo@example.test", "111111", NOW));
        EmailCode latest = codes.saveAndFlush(EmailCode.create("demo@example.test", "222222", NOW));
        entityManager.clear();
        // when
        EmailCode found = codes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc("demo@example.test").orElseThrow();
        // then
        assertThat(found.getEmailCodeId()).isEqualTo(latest.getEmailCodeId());
        assertThat(found.matches("222222")).isTrue();
        assertThat(found.matches("111111")).isFalse();
    }

    @Test
    @DisplayName("틀린 횟수를 다시 읽어도 유지하고 다섯 번 틀린 번호는 인증할 수 없다.")
    void failedAttemptsPersist() {
        // given
        EmailCode code = codes.saveAndFlush(EmailCode.create("attempt@example.test", "123456", NOW));
        for (int index = 0; index < 5; index++) {
            code.recordFailedAttempt();
        }
        entityManager.flush();
        entityManager.clear();
        // when
        EmailCode loaded = codes.findByEmailCodeIdForUpdate(code.getEmailCodeId()).orElseThrow();
        // then
        assertThat(loaded.getAttemptCount()).isEqualTo(5);
        assertThat(loaded.isAttemptsExceeded()).isTrue();
        assertThatThrownBy(() -> loaded.markVerified(NOW.plusMinutes(1))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("인증번호는 정확히 5분부터 만료되고 재발송은 60초부터 가능하다.")
    void verificationAndResendBoundaries() {
        // given
        EmailCode code = EmailCode.create("clock@example.test", "123456", NOW);
        // when / then
        assertThat(code.canResend(NOW.plusSeconds(59))).isFalse();
        assertThat(code.canResend(NOW.plusSeconds(60))).isTrue();
        assertThat(code.isExpired(NOW.plusMinutes(5).minusNanos(1))).isFalse();
        assertThat(code.isExpired(NOW.plusMinutes(5))).isTrue();
        assertThatThrownBy(() -> code.markVerified(NOW.plusMinutes(5))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("인증을 마치면 번호 만료 후에도 발송 30분 전까지 가입할 수 있다.")
    void signupWindowUsesSentTime() {
        // given
        EmailCode code = codes.save(EmailCode.create("verified@example.test", "123456", NOW));
        assertThat(code.canSignUp(NOW)).isFalse();
        code.markVerified(NOW.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();
        // when
        EmailCode loaded = codes.findById(code.getEmailCodeId()).orElseThrow();
        // then
        assertThat(loaded.isVerified()).isTrue();
        assertThat(loaded.canSignUp(NOW.plusMinutes(29))).isTrue();
        assertThat(loaded.canSignUp(NOW.plusMinutes(30))).isFalse();
    }

    @Test
    @DisplayName("재설정 토큰 원문은 저장하지 않고 SHA-256 해시로만 찾는다.")
    void resetOnlyPersistsHash() {
        // given
        User user = users.save(TestFixtures.user("reset@example.test"));
        PasswordResetToken token = resets.saveAndFlush(PasswordResetToken.create(user, "raw-test-reset-token", NOW));
        entityManager.clear();
        // when
        String stored = jdbc.queryForObject("select token from password_reset_token where password_reset_token_id = ?",
                String.class, token.getPasswordResetTokenId());
        // then
        assertThat(stored).hasSize(64).isEqualTo(PasswordResetToken.hashToken("raw-test-reset-token"))
                .isNotEqualTo("raw-test-reset-token");
        assertThat(resets.findByTokenHash("raw-test-reset-token")).isEmpty();
        assertThat(resets.findByTokenHash(stored)).isPresent();
    }

    @Test
    @DisplayName("사용된 재설정 토큰을 다시 읽어도 사용 시각이 남아 재사용할 수 없다.")
    void resetUsePersists() {
        // given
        User user = users.save(TestFixtures.user("used@example.test"));
        resets.saveAndFlush(PasswordResetToken.create(user, "used-reset-token", NOW));
        PasswordResetToken token = resets.findByTokenHashForUpdate(PasswordResetToken.hashToken("used-reset-token")).orElseThrow();
        token.markUsed(NOW.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();
        // when
        PasswordResetToken loaded = resets.findById(token.getPasswordResetTokenId()).orElseThrow();
        // then
        assertThat(loaded.getUsedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThatThrownBy(() -> loaded.markUsed(NOW.plusMinutes(2))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("재설정 링크는 정확히 30분부터 사용할 수 없다.")
    void resetExpiryBoundary() {
        // given
        PasswordResetToken token = PasswordResetToken.create(TestFixtures.user("expired@example.test"), "reset-token", NOW);
        // when / then
        assertThat(token.isExpired(NOW.plusMinutes(30).minusNanos(1))).isFalse();
        assertThat(token.isExpired(NOW.plusMinutes(30))).isTrue();
        assertThatThrownBy(() -> token.markUsed(NOW.plusMinutes(30))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("한 회원의 리프레시 토큰을 폐기해도 다른 회원의 로그인은 유지된다.")
    void revokeAllRefreshesForOneUser() {
        // given
        User owner = users.save(TestFixtures.user("owner@example.test"));
        User other = users.save(TestFixtures.user("other@example.test"));
        refreshes.save(RefreshToken.create(owner, "owner-device-1", NOW, NOW.plusDays(14)));
        refreshes.save(RefreshToken.create(owner, "owner-device-2", NOW, NOW.plusDays(14)));
        refreshes.saveAndFlush(RefreshToken.create(other, "other-device", NOW, NOW.plusDays(14)));
        // when
        refreshes.deleteAllByUserUserId(owner.getUserId());
        entityManager.flush();
        entityManager.clear();
        // then
        assertThat(refreshes.findByToken("owner-device-1")).isEmpty();
        assertThat(refreshes.findByToken("owner-device-2")).isEmpty();
        assertThat(refreshes.findByTokenForUpdate("other-device")).isPresent();
    }

    @Test
    @DisplayName("리프레시 토큰은 DB에서 다시 읽어도 같은 만료 시각을 사용한다.")
    void refreshExpirySurvivesReload() {
        // given
        User user = users.save(TestFixtures.user("refresh@example.test"));
        RefreshToken token = refreshes.saveAndFlush(RefreshToken.create(user, "refresh-test", NOW, NOW.plusDays(14)));
        entityManager.clear();
        // when
        RefreshToken loaded = refreshes.findById(token.getRefreshTokenId()).orElseThrow();
        // then
        assertThat(loaded.isExpired(NOW.plusDays(14).minusNanos(1))).isFalse();
        assertThat(loaded.isExpired(NOW.plusDays(14))).isTrue();
        assertThat(loaded.getUser().getUserId()).isEqualTo(user.getUserId());
    }

    @Test
    @DisplayName("같은 재설정 토큰 해시는 두 번 저장할 수 없다.")
    void uniqueResetToken() {
        // given
        User user = users.save(TestFixtures.user("duplicate-reset@example.test"));
        resets.saveAndFlush(PasswordResetToken.create(user, "same-reset", NOW));
        // when / then
        assertThatThrownBy(() -> resets.saveAndFlush(PasswordResetToken.create(user, "same-reset", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 리프레시 토큰은 두 번 저장할 수 없다.")
    void uniqueRefreshToken() {
        // given
        User user = users.save(TestFixtures.user("duplicate-refresh@example.test"));
        refreshes.saveAndFlush(RefreshToken.create(user, "same-refresh", NOW, NOW.plusDays(14)));
        // when / then
        assertThatThrownBy(() -> refreshes.saveAndFlush(RefreshToken.create(user, "same-refresh", NOW, NOW.plusDays(14))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("재설정 링크를 다시 발급할 때 이전 토큰을 DB에서 없앨 수 있다.")
    void replaceResetLink() {
        // given
        User user = users.save(TestFixtures.user("replace@example.test"));
        resets.saveAndFlush(PasswordResetToken.create(user, "old-reset", NOW));
        // when
        resets.deleteAllByUserUserId(user.getUserId());
        resets.saveAndFlush(PasswordResetToken.create(user, "new-reset", NOW.plusMinutes(1)));
        entityManager.clear();
        // then
        assertThat(resets.findByTokenHash(PasswordResetToken.hashToken("old-reset"))).isEmpty();
        assertThat(resets.findFirstByUserUserIdOrderByCreatedAtDescPasswordResetTokenIdDesc(user.getUserId())).isPresent();
    }
}
