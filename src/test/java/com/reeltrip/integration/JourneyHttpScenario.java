package com.reeltrip.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import javax.imageio.ImageIO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.*;
import com.reeltrip.infra.fetcher.*;
import com.reeltrip.infra.map.*;
import com.reeltrip.infra.storage.StorageClient;

/** 실제 HTTP·JWT·트랜잭션·DB를 연결하고 외부 통신만 고정해요. */
abstract class JourneyHttpScenario {
    private static final String PASSWORD = "JourneyTest123";
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean SourceContentFetcher fetcher;
    @MockitoBean LlmClient llm;
    @MockitoBean PlacesClient places;
    @MockitoBean RoutesClient routes;
    @MockitoBean StorageClient storage;

    @Test
    @DisplayName("로그인 후 multipart 업로드와 일시 장소 검색의 성공·외부 장애 응답을 실제 HTTP로 확인한다.")
    void imageAndSearchFailures() throws Exception {
        // given
        String token = login(account());
        when(storage.store(anyString(), any(), eq("image/png")))
                .thenReturn("https://example.test/user-upload.png").thenThrow(new IOException("unavailable"));
        when(places.search("사용자 검색")).thenReturn(List.of(new MapsPlace("test-place", "검색 장소", "현지 주소",
                33.589, 130.4245, 1, List.of(), "https://maps.google.com/?cid=1", List.of())))
                .thenThrow(new IOException("unavailable"));
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var resource = new ByteArrayResource(bytes.toByteArray()) {
            @Override public String getFilename() { return "photo.png"; }
        };
        var partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new HttpEntity<>(resource, partHeaders));
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // when / then
        var uploaded = http.exchange("/images", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        assertThat(uploaded.getBody().path("imageUrl").asText()).isEqualTo("https://example.test/user-upload.png");
        var failed = http.exchange("/images", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(failed.getStatusCode().value()).isEqualTo(500);
        assertThat(failed.getBody().path("code").asText()).isEqualTo("IMAGE_UPLOAD_FAILED");
        assertThat(call(HttpMethod.GET, "/place-search?query=사용자 검색", token, null, 200).path("places")).hasSize(1);
        assertThat(call(HttpMethod.GET, "/place-search?query=사용자 검색", token, null, 502).path("code").asText()).isEqualTo("PLACE_SEARCH_FAILED");
    }

    @Test
    @DisplayName("실제 로그인→재발급→부분 분석→초안 수정·저장→계획·공유·경로 권한을 HTTP로 연결한다.")
    void loginToSharedPlan() throws Exception {
        // given: 메일은 보류 상태라 가입된 BCrypt 사용자를 준비해요.
        var owner = account();
        var viewer = account();
        var stranger = account();
        String first = login(owner);
        var initial = call(HttpMethod.POST, "/auth/login", null,
                Map.of("email", owner.getEmail(), "password", PASSWORD), 200);
        String oldRefresh = initial.path("refreshToken").asText();
        String token = call(HttpMethod.POST, "/auth/refresh", null,
                Map.of("refreshToken", oldRefresh), 200).path("accessToken").asText();
        assertThat(token).isNotBlank();
        call(HttpMethod.POST, "/auth/refresh", null, Map.of("refreshToken", oldRefresh), 401);
        call(HttpMethod.POST, "/auth/login", null, Map.of("email", owner.getEmail(), "password", "wrong"), 401);
        call(HttpMethod.GET, "/users/me", first, null, 200);
        String viewerToken = login(viewer);
        String strangerToken = login(stranger);
        String good = "https://www.youtube.com/shorts/abcDEF12345?case=" + UUID.randomUUID();
        String bad = "https://www.youtube.com/shorts/abcDEF12346?case=" + UUID.randomUUID();
        when(fetcher.fetch(good)).thenReturn(new FetchedContent(Platform.YOUTUBE, good, null, null));
        when(fetcher.fetch(bad)).thenThrow(new BusinessException(ErrorCode.SOURCE_FETCH_FAILED));
        when(llm.extractCards(any())).thenAnswer(call -> new FakeLlmClient().extractCards(call.getArgument(0)));
        when(routes.route(any(), any(), any())).thenReturn(new RouteEstimate(Duration.ofMinutes(7),
                List.of(new RouteEstimate.Segment("WALK", 7, null, null))));

        // when: 실제 접수 커밋 뒤 비동기 실행을 상태 API로 기다려요.
        long sourceId = call(HttpMethod.POST, "/sources", token, Map.of("urls", List.of(good, bad)), 202).path("sourceId").asLong();
        JsonNode detail = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            detail = call(HttpMethod.GET, "/sources/" + sourceId, token, null, 200);
            if (detail.path("status").asText().equals("DONE")) { break; }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);

        // then: Places 영구 보강은 꺼져 있고, 없는 필드를 사용자가 채워 기존 BE2 저장 API로 넘겨요.
        assertThat(detail.path("status").asText()).isEqualTo("DONE");
        var success = detail.path("sourceLinks").get(0);
        var failure = detail.path("sourceLinks").get(1);
        assertThat(failure.path("status").asText()).isEqualTo("FAILED");
        assertThat(failure.path("code").asText()).isEqualTo("SOURCE_FETCH_FAILED");
        ObjectNode card = success.path("places").get(0).deepCopy();
        assertThat(card.path("sourceLinkId").asLong()).isEqualTo(success.path("sourceLinkId").asLong());
        assertThat(card.path("sourceUrl").asText()).isEqualTo(good);
        assertThat(card.path("sourceTimestampSec").asInt()).isEqualTo(42);
        assertThat(card.path("latitude").isNull()).isTrue();
        verifyNoInteractions(places);
        assertIncompleteCardNotSaved(token, card);
        card.put("address", "사용자가 확인한 주소").put("latitude", 33.589).put("longitude", 130.4245)
                .put("imageUrl", "https://example.test/user-upload.jpg");
        ObjectNode otherCard = card.deepCopy();
        otherCard.put("name", "사용자가 추가한 두 번째 장소").put("latitude", 33.59);
        var saved = call(HttpMethod.POST, "/places", token, Map.of("places", List.of(card, otherCard)), 201);
        long from = saved.path("places").get(0).path("placeId").asLong();
        long to = saved.path("places").get(1).path("placeId").asLong();
        assertThat(call(HttpMethod.GET, "/places", token, null, 200).path("totalCount").asInt()).isEqualTo(2);
        call(HttpMethod.GET, "/sources/" + sourceId, strangerToken, null, 404);
        call(HttpMethod.GET, "/places/" + from, strangerToken, null, 404);

        long planId = call(HttpMethod.POST, "/plans", token, Map.of("name", "통합 검증 여행", "city", "후쿠오카",
                "startDate", "2026-11-20", "endDate", "2026-11-20"), 201).path("planId").asLong();
        var day = Map.of("nodes", List.of(Map.of("placeId", from), Map.of("placeId", to)), "isCompleted", true);
        call(HttpMethod.PUT, "/plans/" + planId + "/days/1", token, day, 200);
        call(HttpMethod.GET, "/plans/" + planId, strangerToken, null, 404);
        call(HttpMethod.POST, "/plans/" + planId + "/share-links", token, Map.of("shareType", "READ_ONLY"), 201);
        assertThat(call(HttpMethod.GET, "/plans/" + planId, viewerToken, null, 200).path("status").asText()).isEqualTo("COMPLETED");
        call(HttpMethod.PUT, "/plans/" + planId + "/days/1", viewerToken, day, 403);
        String route = "/routes?from=" + from + "&to=" + to;
        assertThat(call(HttpMethod.GET, route, viewerToken, null, 200).path("totalMinutes").asInt()).isEqualTo(7);
        call(HttpMethod.GET, route, strangerToken, null, 404);
        verify(routes, times(1)).route(any(), any(), any());
    }

    private User account() {
        return users.save(User.create(UUID.randomUUID() + "@example.test", passwords.encode(PASSWORD),
                "통합검증", false, LocalDateTime.now()));
    }

    /** 빈 필수 항목 AI 카드는 저장되지 않아요. #40(빼고 저장) 전에는 400, 뒤에는 201 + skipped로 빠져요. */
    private void assertIncompleteCardNotSaved(String token, ObjectNode card) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var response = http.exchange("/places", HttpMethod.POST, new HttpEntity<>(Map.of("places", List.of(card)), headers),
                JsonNode.class);
        int status = response.getStatusCode().value();
        assertThat(status).as("POST /places 빈 항목 카드").isIn(400, 201);
        if (status == 201) {
            assertThat(response.getBody().path("places")).isEmpty();
            assertThat(response.getBody().path("skipped")).hasSize(1);
        }
    }

    private String login(User user) {
        return call(HttpMethod.POST, "/auth/login", null, Map.of("email", user.getEmail(), "password", PASSWORD), 200)
                .path("accessToken").asText();
    }

    private JsonNode call(HttpMethod method, String path, String token, Object body, int status) {
        var headers = new HttpHeaders();
        if (token != null) { headers.setBearerAuth(token); }
        var response = http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().value()).as("%s %s", method, path).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
