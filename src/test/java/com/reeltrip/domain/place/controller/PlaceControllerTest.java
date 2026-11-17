package com.reeltrip.domain.place.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.PlaceTestFixtures;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.domain.source.entity.SourceLink;
import com.reeltrip.domain.source.repository.SourceLinkRepository;
import com.reeltrip.domain.source.repository.SourceRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

/** POST/GET /places, GET/PATCH/DELETE /places/{placeId} (S2-1, S3-1, S3-2, S3-3) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaceControllerTest {

    /** 소수 초 없는 서울 시각 */
    private static final String SECONDS_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired SourceRepository sourceRepository;
    @Autowired SourceLinkRepository sourceLinkRepository;

    User me;
    User other;
    String myToken;

    @BeforeEach
    void setUp() {
        me = userRepository.save(TestFixtures.user("place-me@test.com"));
        other = userRepository.save(TestFixtures.user("place-other@test.com"));
        myToken = jwtProvider.createAccessToken(me.getUserId());
    }

    // ---------- POST /places ----------

    @Test
    @DisplayName("직접 추가한 장소 카드를 저장하면 201로 카드 id를 돌려주고 상세에서 같은 값이 보인다.")
    void createPlaces_manualCard_saved() throws Exception {
        // given: 필수 항목을 모두 채운 직접 추가 카드
        Map<String, Object> card = PlaceTestFixtures.foodCard("하카타 잇소");
        // when
        String body = postPlaces(List.of(card))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.places[0].name").value("하카타 잇소"))
                .andExpect(jsonPath("$.places[0].category").value("FOOD"))
                .andReturn().getResponse().getContentAsString();
        long placeId = objectMapper.readTree(body).at("/places/0/placeId").asLong();
        // then: 상세에 저장한 값이 그대로
        mockMvc.perform(get("/places/{placeId}", placeId).header("Authorization", bearer(myToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.sourceLinkId").doesNotExist())
                .andExpect(jsonPath("$.city").value("후쿠오카"))
                .andExpect(jsonPath("$.latitude").value(33.5889))
                .andExpect(jsonPath("$.keyPoints", contains("돈코츠 라멘")))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(SECONDS_DATE_TIME)))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern(SECONDS_DATE_TIME)));
    }

    @Test
    @DisplayName("교통·팁 카드는 주소·좌표·사진 없이 세부유형과 적용지역만 있으면 저장된다.")
    void createPlaces_transportCard_withoutAddress_saved() throws Exception {
        // given: 주소·좌표·사진이 없는 교통 카드
        Map<String, Object> card = PlaceTestFixtures.transportCard("오사카 주유패스");
        // when / then
        postPlaces(List.of(card)).andExpect(status().isCreated());
        assertThat(placeRepository.countByUser_UserId(me.getUserId())).isEqualTo(1);
    }

    @Test
    @DisplayName("직접 추가한 카드는 한 장이라도 장소 카드 필수 항목이 비면 400으로 실패하고 한 장도 저장되지 않는다.")
    void createPlaces_oneMissingAddress_noneSaved() throws Exception {
        // given: 두 번째 카드에 주소가 없음
        Map<String, Object> bad = PlaceTestFixtures.foodCard("주소 없는 카드");
        bad.remove("address");
        // when / then
        postPlaces(List.of(PlaceTestFixtures.foodCard("정상 카드"), bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(placeRepository.countByUser_UserId(me.getUserId())).isZero();
    }

    @Test
    @DisplayName("교통·팁 카드에 세부유형이나 적용지역이 없으면 저장할 수 없다.")
    void createPlaces_transportWithoutSubType_invalid() throws Exception {
        // given
        Map<String, Object> card = PlaceTestFixtures.transportCard("패스");
        card.remove("applicableRegion");
        // when / then
        postPlaces(List.of(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("핵심 포인트가 3개를 넘으면 저장할 수 없다.")
    void createPlaces_fourKeyPoints_invalid() throws Exception {
        // given
        Map<String, Object> card = PlaceTestFixtures.foodCard("포인트 많은 카드");
        card.put("keyPoints", List.of("라멘", "교자", "볶음밥", "맥주"));
        // when / then
        postPlaces(List.of(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("가격·메모·장소명이 50자를 넘으면 저장할 수 없고, 정확히 50자는 저장된다.")
    void createPlaces_over50Chars_invalid() throws Exception {
        String fifty = "가".repeat(50);
        String fiftyOne = "가".repeat(51);
        for (String field : List.of("priceText", "memo", "name")) {
            // given: 51자
            Map<String, Object> card = PlaceTestFixtures.foodCard("카드");
            card.put(field, fiftyOne);
            // when / then
            postPlaces(List.of(card))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        // given: 50자는 됨
        Map<String, Object> card = PlaceTestFixtures.foodCard(fifty);
        card.put("priceText", fifty);
        card.put("memo", fifty);
        // when / then
        postPlaces(List.of(card)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AI가 만든 카드는 원본 링크가 있어야 저장되고, 내 분석 링크의 카드로 저장된다.")
    void createPlaces_aiCard_requiresSourceUrl() throws Exception {
        // given: 내가 분석한 링크
        SourceLink link = saveSourceLink(me);
        Map<String, Object> card = PlaceTestFixtures.foodCard("AI 카드");
        card.put("sourceLinkId", link.getSourceLinkId());
        // when / then: 원본 링크가 없으면 실패
        postPlaces(List.of(card)).andExpect(status().isBadRequest());
        // when / then: 원본 링크가 있으면 저장되고 상세에 sourceLinkId가 보임
        card.put("sourceUrl", "https://www.youtube.com/shorts/test");
        String body = postPlaces(List.of(card)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long placeId = objectMapper.readTree(body).at("/places/0/placeId").asLong();
        mockMvc.perform(get("/places/{placeId}", placeId).header("Authorization", bearer(myToken)))
                .andExpect(jsonPath("$.sourceLinkId").value(link.getSourceLinkId()))
                .andExpect(jsonPath("$.sourceUrl").value("https://www.youtube.com/shorts/test"));
    }

    @Test
    @DisplayName("다른 사람이 분석한 링크로 카드를 저장하면 400으로 실패한다.")
    void createPlaces_othersSourceLink_invalid() throws Exception {
        // given: 남의 분석 링크
        SourceLink othersLink = saveSourceLink(other);
        Map<String, Object> card = PlaceTestFixtures.foodCard("남의 링크 카드");
        card.put("sourceLinkId", othersLink.getSourceLinkId());
        card.put("sourceUrl", "https://www.youtube.com/shorts/test");
        // when / then
        postPlaces(List.of(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // S2-1 "모두 저장": Places 보강을 못 한 AI 카드만 빼고 나머지는 저장해요 (decisions 2026-11-14).

    @Test
    @DisplayName("AI 카드 중 주소·사진이나 적용지역이 빈 카드는 빼고 나머지만 저장하며, 뺀 카드의 순서와 빈 항목을 알려준다.")
    void createPlaces_aiCardsMissingFields_skippedAndRestSaved() throws Exception {
        // given: 다 채운 AI 카드, 주소·사진이 빈 AI 카드, 적용지역이 빈 AI 교통 카드
        SourceLink link = saveSourceLink(me);
        Map<String, Object> full = aiCard(PlaceTestFixtures.foodCard("다 채운 카드"), link);
        Map<String, Object> noAddress = aiCard(PlaceTestFixtures.foodCard("주소 없는 카드"), link);
        noAddress.remove("address");
        noAddress.remove("imageUrl");
        Map<String, Object> noRegion = aiCard(PlaceTestFixtures.transportCard("지역 없는 패스"), link);
        noRegion.remove("applicableRegion");
        // when / then
        postPlaces(List.of(full, noAddress, noRegion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.places", hasSize(1)))
                .andExpect(jsonPath("$.places[0].name").value("다 채운 카드"))
                .andExpect(jsonPath("$.skipped", hasSize(2)))
                .andExpect(jsonPath("$.skipped[0].index").value(1))
                .andExpect(jsonPath("$.skipped[0].sourceLinkId").value(link.getSourceLinkId()))
                .andExpect(jsonPath("$.skipped[0].name").value("주소 없는 카드"))
                .andExpect(jsonPath("$.skipped[0].missingFields", contains("imageUrl", "address")))
                .andExpect(jsonPath("$.skipped[1].index").value(2))
                .andExpect(jsonPath("$.skipped[1].missingFields", contains("applicableRegion")));
        assertThat(placeRepository.countByUser_UserId(me.getUserId())).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 카드가 모두 좌표가 비었으면 한 장도 저장하지 않고 201과 빈 목록, 뺀 카드 목록을 돌려준다.")
    void createPlaces_allAiCardsMissingCoordinates_noneSaved() throws Exception {
        // given: 좌표가 빈 AI 카드 두 장
        SourceLink link = saveSourceLink(me);
        Map<String, Object> first = aiCard(PlaceTestFixtures.foodCard("좌표 없는 카드1"), link);
        first.remove("latitude");
        first.remove("longitude");
        Map<String, Object> second = aiCard(PlaceTestFixtures.foodCard("좌표 없는 카드2"), link);
        second.remove("longitude");
        // when / then
        postPlaces(List.of(first, second))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.places", hasSize(0)))
                .andExpect(jsonPath("$.skipped[0].missingFields", contains("latitude", "longitude")))
                .andExpect(jsonPath("$.skipped[1].missingFields", contains("longitude")));
        assertThat(placeRepository.countByUser_UserId(me.getUserId())).isZero();
    }

    @Test
    @DisplayName("빈 항목이 있는 AI 카드라도 글자 수 초과 같은 형식 오류가 있으면 400으로 실패하고 한 장도 저장되지 않는다.")
    void createPlaces_aiCardMissingAndTooLong_noneSaved() throws Exception {
        // given: 다 채운 카드 + 주소가 비고 가격이 51자인 카드
        SourceLink link = saveSourceLink(me);
        Map<String, Object> bad = aiCard(PlaceTestFixtures.foodCard("형식 오류 카드"), link);
        bad.remove("address");
        bad.put("priceText", "가".repeat(51));
        // when / then
        postPlaces(List.of(aiCard(PlaceTestFixtures.foodCard("정상 카드"), link), bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(placeRepository.countByUser_UserId(me.getUserId())).isZero();
    }

    @Test
    @DisplayName("빼야 할 카드가 없으면 skipped는 빈 목록이다.")
    void createPlaces_nothingSkipped_emptySkipped() throws Exception {
        postPlaces(List.of(PlaceTestFixtures.foodCard("정상 카드")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skipped", hasSize(0)));
    }

    @Test
    @DisplayName("로그인하지 않으면 카드 API는 모두 401로 실패한다.")
    void placesApi_withoutLogin_unauthorized() throws Exception {
        // given: 토큰 없음
        String body = objectMapper.writeValueAsString(Map.of("places", List.of(PlaceTestFixtures.foodCard("x"))));
        // when / then
        mockMvc.perform(post("/places").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/places")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/places/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/places/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/places/1")).andExpect(status().isUnauthorized());
    }

    // ---------- GET /places/{placeId} ----------

    @Test
    @DisplayName("다른 사람의 카드나 없는 카드를 열면 있다는 것도 알리지 않고 404로 실패한다.")
    void getPlace_othersOrMissing_notFound() throws Exception {
        // given: 남의 카드
        Place othersPlace = placeRepository.save(
                Place.create(other, null, TestFixtures.foodContent("남의 카드", "후쿠오카")));
        // when / then
        mockMvc.perform(get("/places/{placeId}", othersPlace.getPlaceId()).header("Authorization", bearer(myToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        mockMvc.perform(get("/places/{placeId}", 999_999).header("Authorization", bearer(myToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    // ---------- GET /places ----------

    @Test
    @DisplayName("보관함 목록은 최신순이고, 카테고리별 개수는 8개 키를 0까지 담으며 카테고리 필터와 관계없이 센다.")
    void getPlaces_categoryCounts_ignoreCategoryFilter() throws Exception {
        // given: 맛집 2, 카페 1, 팁 1, 남의 맛집 1
        Place food1 = savePlace(me, PlaceTestFixtures.placeAt(Category.FOOD, "맛집1", 34.69, 135.53));
        Place food2 = savePlace(me, PlaceTestFixtures.placeAt(Category.FOOD, "맛집2", 34.69, 135.53));
        savePlace(me, PlaceTestFixtures.placeAt(Category.CAFE, "카페", 34.69, 135.53));
        savePlace(me, PlaceTestFixtures.tip("환전 팁"));
        savePlace(other, PlaceTestFixtures.placeAt(Category.FOOD, "남의 맛집", 34.69, 135.53));
        // when: 맛집만 보면
        mockMvc.perform(get("/places").param("category", "FOOD").header("Authorization", bearer(myToken)))
                // then: 내 맛집만 최신순, 개수는 전체 기준
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[*].placeId",
                        contains(food2.getPlaceId().intValue(), food1.getPlaceId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.categoryCounts.FOOD").value(2))
                .andExpect(jsonPath("$.categoryCounts.CAFE").value(1))
                .andExpect(jsonPath("$.categoryCounts.TIP").value(1))
                .andExpect(jsonPath("$.categoryCounts.STAY").value(0))
                .andExpect(jsonPath("$.categoryCounts.SIGHTSEEING").value(0))
                .andExpect(jsonPath("$.categoryCounts.ACTIVITY").value(0))
                .andExpect(jsonPath("$.categoryCounts.SHOPPING").value(0))
                .andExpect(jsonPath("$.categoryCounts.TRANSPORT").value(0));
    }

    @Test
    @DisplayName("보관함 목록은 카드에 바로 보여줄 가는 길·영업시간과, 교통·팁의 세부유형·적용지역·비용을 같이 내려준다.")
    void getPlaces_includesCardSummaryFields() throws Exception {
        // given: 가는 길·영업시간이 있는 맛집 카드와 교통 카드
        Map<String, Object> food = PlaceTestFixtures.foodCard("하카타 잇소");
        food.put("accessText", "하카타역 동쪽 도보 약 6~7분");
        food.put("openingHours", "11:00-24:00");
        postPlaces(List.of(food, PlaceTestFixtures.transportCard("오사카 주유패스"))).andExpect(status().isCreated());
        // when / then
        mockMvc.perform(get("/places").param("category", "FOOD").header("Authorization", bearer(myToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].accessText").value("하카타역 동쪽 도보 약 6~7분"))
                .andExpect(jsonPath("$.places[0].openingHours").value("11:00-24:00"))
                .andExpect(jsonPath("$.places[0].subType").value((Object) null));
        mockMvc.perform(get("/places").param("category", "TRANSPORT").header("Authorization", bearer(myToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].subType").value("교통패스"))
                .andExpect(jsonPath("$.places[0].applicableRegion").value("오사카"))
                .andExpect(jsonPath("$.places[0].cost").value("¥3,300 (1일권)"));
    }

    @Test
    @DisplayName("보관함 목록은 page·size로 잘라서 내려주고 totalCount는 전체 개수다.")
    void getPlaces_paging() throws Exception {
        // given: 카드 3장
        savePlace(me, PlaceTestFixtures.placeAt(Category.FOOD, "1", 34.69, 135.53));
        savePlace(me, PlaceTestFixtures.placeAt(Category.FOOD, "2", 34.69, 135.53));
        savePlace(me, PlaceTestFixtures.placeAt(Category.FOOD, "3", 34.69, 135.53));
        // when: 2장씩 두 번째 페이지
        mockMvc.perform(get("/places").param("page", "1").param("size", "2")
                        .header("Authorization", bearer(myToken)))
                // then: 가장 오래된 카드 한 장
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places", hasSize(1)))
                .andExpect(jsonPath("$.places[0].name").value("1"))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    @DisplayName("구역 좌표와 반경을 주면 반경 안의 좌표 있는 카드만 나오고 카테고리별 개수에도 반경이 적용된다.")
    void getPlaces_radiusFilter() throws Exception {
        // given: 오사카성(34.6873, 135.5262) 근처 카페(약 450m), 도톤보리 맛집(약 3km), 좌표 없는 팁
        Place near = savePlace(me, PlaceTestFixtures.placeAt(Category.CAFE, "성 근처 카페", 34.6900, 135.5300));
        savePlace(me, PlaceTestFixtures.placeAt(Category.FOOD, "도톤보리 맛집", 34.6687, 135.5013));
        savePlace(me, PlaceTestFixtures.tip("환전 팁"));
        // when: 반경 기본값(1500m)으로 보면
        mockMvc.perform(get("/places").param("latitude", "34.6873").param("longitude", "135.5262")
                        .header("Authorization", bearer(myToken)))
                // then: 근처 카페만
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[*].placeId", contains(near.getPlaceId().intValue())))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.categoryCounts.CAFE").value(1))
                .andExpect(jsonPath("$.categoryCounts.FOOD").value(0))
                .andExpect(jsonPath("$.categoryCounts.TIP").value(0));
        // when: 반경을 5km로 넓히면 도톤보리도 들어옴
        mockMvc.perform(get("/places").param("latitude", "34.6873").param("longitude", "135.5262")
                        .param("radiusM", "5000").header("Authorization", bearer(myToken)))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("위도·경도 중 하나만 보내거나 없는 카테고리·정렬을 보내면 400으로 실패한다.")
    void getPlaces_invalidQuery_badRequest() throws Exception {
        // given / when / then
        mockMvc.perform(get("/places").param("latitude", "34.6873").header("Authorization", bearer(myToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/places").param("category", "NOPE").header("Authorization", bearer(myToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/places").param("sortType", "OLDEST").header("Authorization", bearer(myToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/places").param("size", "0").header("Authorization", bearer(myToken)))
                .andExpect(status().isBadRequest());
    }

    // ---------- PATCH /places/{placeId} ----------

    @Test
    @DisplayName("카드를 수정하면 보낸 필드만 바뀌고, null로 보낸 선택 항목은 비워진다.")
    void modifyPlace_onlySentFieldsChange() throws Exception {
        // given: 메모가 있는 카드
        Map<String, Object> card = PlaceTestFixtures.foodCard("하카타 잇소");
        card.put("memo", "줄이 길어요");
        String created = postPlaces(List.of(card)).andReturn().getResponse().getContentAsString();
        long placeId = objectMapper.readTree(created).at("/places/0/placeId").asLong();
        // when: 이름과 핵심 포인트를 바꾸고 메모는 null로
        String patchBody = """
                { "name": "하카타 잇소 본점", "keyPoints": ["라멘", "교자"], "memo": null }
                """;
        mockMvc.perform(patch("/places/{placeId}", placeId).header("Authorization", bearer(myToken))
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                // then: 바꾼 것만 바뀌고 나머지는 그대로
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("하카타 잇소 본점"))
                .andExpect(jsonPath("$.keyPoints", contains("라멘", "교자")))
                .andExpect(jsonPath("$.memo").doesNotExist())
                .andExpect(jsonPath("$.priceText").value("¥960~980"))
                .andExpect(jsonPath("$.address").value("福岡市博多区博多駅東3-1-6"));
    }

    @Test
    @DisplayName("수정으로 필수 항목을 비우거나 핵심 포인트를 4개로 만들면 400으로 실패하고 카드는 그대로다.")
    void modifyPlace_clearRequired_invalid() throws Exception {
        // given
        Place place = savePlace(me, TestFixtures.foodContent("하카타 잇소", "후쿠오카"));
        // when / then: 주소를 비우면 실패
        mockMvc.perform(patch("/places/{placeId}", place.getPlaceId()).header("Authorization", bearer(myToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"address\": null }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(patch("/places/{placeId}", place.getPlaceId()).header("Authorization", bearer(myToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"keyPoints\": [\"a\", \"b\", \"c\", \"d\"] }"))
                .andExpect(status().isBadRequest());
        // then: 카드는 그대로
        mockMvc.perform(get("/places/{placeId}", place.getPlaceId()).header("Authorization", bearer(myToken)))
                .andExpect(jsonPath("$.address").value("福岡市博多区博多駅東3-1-6"))
                .andExpect(jsonPath("$.keyPoints", hasSize(1)));
    }

    @Test
    @DisplayName("다른 사람의 카드를 수정하면 404로 실패한다.")
    void modifyPlace_others_notFound() throws Exception {
        // given
        Place othersPlace = savePlace(other, TestFixtures.foodContent("남의 카드", "후쿠오카"));
        // when / then
        mockMvc.perform(patch("/places/{placeId}", othersPlace.getPlaceId()).header("Authorization", bearer(myToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"name\": \"바꿈\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    // ---------- DELETE /places/{placeId} ----------

    @Test
    @DisplayName("다른 사람의 카드를 지우면 404로 실패하고 카드는 남는다.")
    void removePlace_others_notFound() throws Exception {
        // given
        Place othersPlace = savePlace(other, TestFixtures.foodContent("남의 카드", "후쿠오카"));
        // when / then
        mockMvc.perform(delete("/places/{placeId}", othersPlace.getPlaceId()).header("Authorization", bearer(myToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        assertThat(placeRepository.findById(othersPlace.getPlaceId())).isPresent();
    }

    // ---------- 도우미 ----------

    private ResultActions postPlaces(List<Map<String, Object>> cards) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("places", cards));
        return mockMvc.perform(post("/places").header("Authorization", bearer(myToken))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Place savePlace(User owner, PlaceContent content) {
        return placeRepository.save(Place.create(owner, null, content));
    }

    private SourceLink saveSourceLink(User owner) {
        Source source = sourceRepository.save(Source.create(owner));
        return sourceLinkRepository.save(
                SourceLink.create(source, "https://www.youtube.com/shorts/test", Platform.YOUTUBE));
    }

    /** 분석 링크에서 나온 카드로 바꿔요. FE는 GET /sources 초안의 sourceLinkId·sourceUrl을 그대로 보내요. */
    private static Map<String, Object> aiCard(Map<String, Object> card, SourceLink link) {
        card.put("sourceLinkId", link.getSourceLinkId());
        card.put("sourceUrl", link.getUrl());
        return card;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
