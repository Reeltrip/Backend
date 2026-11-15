package com.reeltrip.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.PlanEditFixtures;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** 요약카드 휴지통 (#58, Figma 3-2·3-3). 지우면 휴지통으로, 복구하면 보관함으로만 돌아와요(계획 자리는 복구 안 함). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaceTrashTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    User me;
    User other;
    Place ramen;
    Place cafe;
    Place othersCard;

    @BeforeEach
    void setUp() {
        me = userRepository.save(TestFixtures.user("trash-me@test.com"));
        other = userRepository.save(TestFixtures.user("trash-other@test.com"));
        ramen = placeRepository.save(PlanEditFixtures.place(me, Category.FOOD, "하카타 잇소"));
        cafe = placeRepository.save(PlanEditFixtures.place(me, Category.CAFE, "렉 커피"));
        othersCard = placeRepository.save(PlanEditFixtures.place(other, Category.FOOD, "남의 카드"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getUserId());
    }

    private void trash(Place place) throws Exception {
        mockMvc.perform(delete("/places/{placeId}", place.getPlaceId()).header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("카드를 지우면 휴지통으로 옮겨져 보관함·상세·개수에서 빠지고 휴지통 목록에 보인다.")
    void removePlace_movesToTrash() throws Exception {
        // when
        trash(ramen);

        // then: 보관함·상세·내 정보 개수에서 빠짐
        mockMvc.perform(get("/places").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places[0].placeId").value(cafe.getPlaceId()))
                .andExpect(jsonPath("$.categoryCounts.FOOD").value(0));
        mockMvc.perform(get("/places/{placeId}", ramen.getPlaceId()).header("Authorization", bearer(me)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/users/me").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.placeCount").value(1));
        // 휴지통에는 보임 (DB 행은 남아 있음)
        mockMvc.perform(get("/places/trash").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places[0].placeId").value(ramen.getPlaceId()))
                .andExpect(jsonPath("$.places[0].name").value("하카타 잇소"))
                .andExpect(jsonPath("$.places[0].deletedAt").exists());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE place_id = ?", Integer.class,
                ramen.getPlaceId())).isEqualTo(1);
    }

    @Test
    @DisplayName("휴지통의 카드를 복구하면 보관함으로 돌아오고 휴지통에서 빠진다.")
    void restore_backToStorage() throws Exception {
        // given
        trash(ramen);
        trash(cafe);

        // when
        mockMvc.perform(post("/places/trash/restore").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeIds\":[" + ramen.getPlaceId() + "," + cafe.getPlaceId() + "]}"))
                .andExpect(status().isNoContent());
        em.clear();

        // then
        mockMvc.perform(get("/places").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.totalCount").value(2));
        mockMvc.perform(get("/places/trash").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.places", empty()));
    }

    @Test
    @DisplayName("계획에 쓰인 카드를 지우면 계획에서 빠지고, 복구해도 보관함으로만 돌아오고 계획 자리는 돌아오지 않는다.")
    void restore_doesNotReturnToPlan() throws Exception {
        // given: 1일차에 라멘 노드, 숙소 없음
        Plan plan = PlanEditFixtures.plan(me, 1);
        plan.getDays().getFirst().addNode(ramen, 0);
        planRepository.save(plan);
        em.flush();

        // when: 지웠다가 복구
        trash(ramen);
        mockMvc.perform(post("/places/trash/restore").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeIds\":[" + ramen.getPlaceId() + "]}"))
                .andExpect(status().isNoContent());
        em.clear();

        // then: 보관함에는 있고 계획 노드는 비어 있음
        mockMvc.perform(get("/places/{placeId}", ramen.getPlaceId()).header("Authorization", bearer(me)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.days[0].nodes", empty()));
    }

    @Test
    @DisplayName("복구할 카드에 내 휴지통에 없는 카드(보관함의 카드·남의 카드)가 섞이면 404이고 아무것도 복구되지 않는다.")
    void restore_notInMyTrash_nothingChanges() throws Exception {
        // given
        trash(ramen);

        // when / then: 보관함에 있는 cafe가 섞임
        mockMvc.perform(post("/places/trash/restore").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeIds\":[" + ramen.getPlaceId() + "," + cafe.getPlaceId() + "]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
        // 남의 카드가 섞임
        mockMvc.perform(post("/places/trash/restore").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeIds\":[" + ramen.getPlaceId() + "," + othersCard.getPlaceId() + "]}"))
                .andExpect(status().isNotFound());
        em.clear();
        mockMvc.perform(get("/places/trash").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.places[*].placeId", contains(ramen.getPlaceId().intValue())));
    }

    @Test
    @DisplayName("휴지통에서 삭제하면 완전히 지워져 휴지통·보관함 어디에도 없고 DB 행도 사라진다.")
    void purge_deletesForever() throws Exception {
        // given
        trash(ramen);
        trash(cafe);

        // when
        mockMvc.perform(delete("/places/trash").header("Authorization", bearer(me))
                        .param("placeIds", ramen.getPlaceId().toString()))
                .andExpect(status().isNoContent());
        em.clear();

        // then
        mockMvc.perform(get("/places/trash").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.places[*].placeId", contains(cafe.getPlaceId().intValue())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE place_id = ?", Integer.class,
                ramen.getPlaceId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_key_point WHERE place_id = ?", Integer.class,
                ramen.getPlaceId())).isZero();
    }

    @Test
    @DisplayName("보관함에 있는 카드는 휴지통 삭제로 지울 수 없고 404이다.")
    void purge_notInTrash_notFound() throws Exception {
        mockMvc.perform(delete("/places/trash").header("Authorization", bearer(me))
                        .param("placeIds", cafe.getPlaceId().toString()))
                .andExpect(status().isNotFound());
        assertThat(placeRepository.findById(cafe.getPlaceId())).isPresent();
    }

    @Test
    @DisplayName("남의 휴지통은 보이지 않고, 휴지통의 카드는 계획을 만들 때 불러오지 않는다.")
    void trash_isPrivateAndExcludedFromPlans() throws Exception {
        // given
        trash(ramen);

        // then: 다른 회원의 휴지통은 비어 있음
        mockMvc.perform(get("/places/trash").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.totalCount").value(0));
        // 계획 만들기: 후쿠오카 카드 중 휴지통 카드는 빼고 1장만 불러옴
        mockMvc.perform(post("/plans").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"후쿠오카\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-01\","
                                + "\"city\":\"후쿠오카\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadedPlaceCount").value(1));
    }

    @Test
    @DisplayName("휴지통의 카드는 다시 지우거나 고칠 수 없고 404이다.")
    void trashedCard_cannotBeModified() throws Exception {
        trash(ramen);
        mockMvc.perform(delete("/places/{placeId}", ramen.getPlaceId()).header("Authorization", bearer(me)))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/places/{placeId}", ramen.getPlaceId()).header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"수정\"}"))
                .andExpect(status().isNotFound());
    }
}
