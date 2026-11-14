package com.reeltrip.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.reeltrip.domain.user.dto.UserMeResponse;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
class UserMeHttpTest {

    @Autowired TestRestTemplate client;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;

    @Test
    @DisplayName("실제 HTTP에서도 인증한 회원만 자신의 정보와 개수를 받을 수 있다.")
    void liveHttpMeRequiresAuthentication() {
        // given
        var user = users.save(TestFixtures.user(UUID.randomUUID() + "@example.test"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.createAccessToken(user.getUserId()));
        // when
        var response = client.exchange("/users/me", HttpMethod.GET, new HttpEntity<>(headers), UserMeResponse.class);
        var anonymous = client.getForEntity("/users/me", String.class);
        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo(user.getUserId());
        assertThat(response.getBody().placeCount()).isZero();
        assertThat(response.getBody().planCount()).isZero();
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).contains("UNAUTHORIZED");
    }
}
