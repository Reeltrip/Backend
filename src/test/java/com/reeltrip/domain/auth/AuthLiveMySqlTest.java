package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.reeltrip.domain.auth.dto.LoginRequest;
import com.reeltrip.domain.auth.dto.LoginResponse;
import com.reeltrip.domain.auth.dto.RefreshRequest;
import com.reeltrip.domain.auth.dto.TokenResponse;
import com.reeltrip.domain.auth.repository.RefreshTokenRepository;
import com.reeltrip.domain.auth.service.AuthService;
import com.reeltrip.domain.auth.service.RefreshTokenCodec;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=${REELTRIP_AUTH_MYSQL_URL}",
        "spring.datasource.username=root",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout = 5"
})
@EnabledIfEnvironmentVariable(named = "REELTRIP_AUTH_MYSQL_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_issue12.*")
class AuthLiveMySqlTest {

    private static final String PASSWORD = "MySqlLogin123";

    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService service;
    @Autowired RefreshTokenCodec codec;
    @Autowired RefreshTokenRepository refreshes;

    @Test
    @DisplayName("실제 HTTP와 MySQL에서 로그인·재발급·이전 토큰 거부·보호된 API 접근이 동작한다.")
    void liveHttpLoginAndRefresh() {
        // given
        User user = createUser();
        // when
        var login = client.postForEntity("/auth/login", new LoginRequest(user.getEmail(), PASSWORD), LoginResponse.class);
        // then
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().getCacheControl()).isEqualTo("no-store");
        LoginResponse first = login.getBody();
        assertThat(first).isNotNull();
        var refreshed = client.postForEntity("/auth/refresh", new RefreshRequest(first.refreshToken()), TokenResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse next = refreshed.getBody();
        assertThat(next).isNotNull();
        assertThat(next.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(refreshes.findByToken(codec.hash(first.refreshToken()))).isEmpty();
        assertThat(refreshes.findByToken(codec.hash(next.refreshToken()))).isPresent();

        var replay = client.postForEntity("/auth/refresh", new RefreshRequest(first.refreshToken()), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody()).contains("UNAUTHORIZED");
        var wrong = client.postForEntity("/auth/login", new LoginRequest(user.getEmail(), "wrong"), String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).contains("INVALID_LOGIN");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(next.accessToken());
        var places = client.exchange("/places", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        var plans = client.exchange("/plans?owner=me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(places.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plans.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("실제 MySQL 행 잠금에서도 같은 토큰의 동시 재발급은 한 번만 성공한다.")
    void mysqlConcurrentRotation() throws Exception {
        // given
        User user = createUser();
        String token = service.login(new LoginRequest(user.getEmail(), PASSWORD)).refreshToken();
        // when / then
        ConcurrentRefreshAssertions.assertOnlyOneSuccess(service, token);
    }

    private User createUser() {
        return users.save(User.create(UUID.randomUUID() + "@example.test", encoder.encode(PASSWORD),
                "실서버", false, LocalDateTime.of(2026, 11, 14, 10, 0)));
    }
}
