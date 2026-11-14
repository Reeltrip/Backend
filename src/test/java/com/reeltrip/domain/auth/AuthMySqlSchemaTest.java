package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.auth.entity.EmailCode;
import com.reeltrip.domain.auth.entity.PasswordResetToken;
import com.reeltrip.domain.auth.entity.RefreshToken;
import com.reeltrip.domain.auth.repository.EmailCodeRepository;
import com.reeltrip.domain.auth.repository.PasswordResetTokenRepository;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(properties = {
        "spring.datasource.url=${REELTRIP_MYSQL_URL}",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named = "REELTRIP_MYSQL_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_issue9.*")
@Transactional
class AuthMySqlSchemaTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EmailCodeRepository codes;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired RefreshTokenRepository refreshes;

    @Test
    @DisplayName("수동 SQL을 적용한 MySQL에서 validate와 인증 저장이 모두 동작한다.")
    void manualSchemaMatchesJpa() {
        // given: 새 임시 MySQL에 docs/schema.sql을 수동 적용하고 validate로 시작한 컨텍스트
        LocalDateTime now = LocalDateTime.of(2026, 11, 14, 10, 0);
        var user = users.save(TestFixtures.user("mysql@example.test"));
        // when
        codes.saveAndFlush(EmailCode.create(user.getEmail(), "123456", now));
        resets.saveAndFlush(PasswordResetToken.create(user, "mysql-reset", now));
        refreshes.saveAndFlush(RefreshToken.create(user, "mysql-refresh", now, now.plusDays(14)));
        // then
        assertThat(codes.findFirstByEmailOrderByCreatedAtDescEmailCodeIdDesc(user.getEmail())).isPresent();
        assertThat(resets.findByTokenHashForUpdate(PasswordResetToken.hashToken("mysql-reset"))).isPresent();
        assertThat(refreshes.findByTokenForUpdate("mysql-refresh")).isPresent();
        assertThat(jdbc.queryForObject("select token from password_reset_token where user_id = ?",
                String.class, user.getUserId())).isEqualTo(PasswordResetToken.hashToken("mysql-reset"));
        for (String table : new String[]{"user", "password_reset_token", "refresh_token"}) {
            String column = table.equals("user") ? "email" : "token";
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.statistics
                    where table_schema = database() and table_name = ? and column_name = ? and non_unique = 0
                    """, Integer.class, table, column)).isPositive();
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'email_code'
                and index_name = 'idx_email_code_email_created'
                """, Integer.class)).isEqualTo(2);
    }
}
