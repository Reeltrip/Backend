package com.reeltrip.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;

/** 마이페이지 API 4개를 실제 HTTP로 한 흐름으로 불러 봐요 (#79). PATCH 때문에 JDK HttpClient를 써요. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
class MyPageHttpTest {

    private static final String PASSWORD = "MyPagePass123";

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("실제 HTTP에서 로그인 → 프로필 수정 → 문의 접수 → 로그아웃 뒤에는 그 리프레시 토큰으로 재발급할 수 없다.")
    void myPageFlowOverHttp() throws Exception {
        // given: 로그인한 회원
        User user = users.save(User.create(UUID.randomUUID() + "@example.test", encoder.encode(PASSWORD),
                "여행자", false, LocalDateTime.of(2026, 11, 1, 0, 0)));
        JsonNode login = json(send("POST", "/auth/login", null,
                "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"));
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        // when: 프로필 수정
        var patched = send("PATCH", "/users/me", access,
                "{\"nickname\":\"보영\",\"avatarUrl\":\"https://storage.googleapis.com/b/users/1/a.png\"}");
        var me = send("GET", "/users/me", access, null);
        var badPatch = send("PATCH", "/users/me", access, "{\"avatarUrl\":\"javascript:alert(1)\"}");
        // then
        assertThat(patched.statusCode()).isEqualTo(200);
        assertThat(json(me).get("nickname").asText()).isEqualTo("보영");
        assertThat(json(me).get("avatarUrl").asText()).isEqualTo("https://storage.googleapis.com/b/users/1/a.png");
        assertThat(badPatch.statusCode()).isEqualTo(400);
        assertThat(json(badPatch).get("code").asText()).isEqualTo("INVALID_INPUT");

        // when: 문의 접수
        var inquiry = send("POST", "/inquiries", access,
                "{\"type\":\"ACCOUNT_LOGIN\",\"content\":\"로그인 문의\",\"wantsReplyNotification\":true}");
        var anonymousInquiry = send("POST", "/inquiries", null,
                "{\"type\":\"ACCOUNT_LOGIN\",\"content\":\"로그인 문의\",\"wantsReplyNotification\":true}");
        // then
        assertThat(inquiry.statusCode()).isEqualTo(201);
        assertThat(json(inquiry).get("inquiryId").isNumber()).isTrue();
        assertThat(json(inquiry).get("createdAt").asText()).endsWith("+09:00");
        assertThat(anonymousInquiry.statusCode()).isEqualTo(401);

        // when: 로그아웃
        var logout = send("POST", "/auth/logout", null, "{\"refreshToken\":\"" + refresh + "\"}");
        var reissue = send("POST", "/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}");
        // then
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertThat(reissue.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path, String accessToken, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }
}
