package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

import jakarta.persistence.EntityManager;

/** PUT /plans/{planId}/days/{dayNumber} (S4-5 동선 짜기) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanEditDayTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired PlanRepository planRepository;
    @Autowired EntityManager em;

    User owner;
    User editor;
    User viewer;
    User stranger;
    Place ramen;
    Place sushi;
    Place udon;
    Place cafe;
    Plan plan;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestFixtures.user("day-owner@test.com"));
        editor = userRepository.save(TestFixtures.user("day-editor@test.com"));
        viewer = userRepository.save(TestFixtures.user("day-viewer@test.com"));
        stranger = userRepository.save(TestFixtures.user("day-stranger@test.com"));
        ramen = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "하카타 잇소"));
        sushi = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "효탄 스시"));
        udon = placeRepository.save(PlanEditFixtures.place(owner, Category.FOOD, "우동 타이라"));
        cafe = placeRepository.save(PlanEditFixtures.place(owner, Category.CAFE, "노 커피"));
        plan = PlanEditFixtures.plan(owner, 2);
        plan.addMember(editor, PlanRole.EDITOR);
        plan.addMember(viewer, PlanRole.VIEWER);
        planRepository.save(plan);
        em.flush();
    }

    @Test
    @DisplayName("그 날의 노드를 통째로 저장하면 배열 순서대로 순번이 다시 매겨지고 빠진 노드는 지워진다.")
    void saveDay_replacesWholeOrder() throws Exception {
        // given: 1일차에 라멘·스시·우동 순으로 확정된 노드
        PlanDay day1 = plan.getDays().get(0);
        PlanNode ramenNode = day1.addNode(ramen, 0);
        day1.addNode(sushi, 1);
        day1.addNode(udon, 2);
        em.flush();
        em.clear();

        // when: 우동·라멘 순으로 저장하면
        saveDay(owner, 1, nodes(node(udon), node(ramen)), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].placeId").value(udon.getPlaceId()))
                .andExpect(jsonPath("$.nodes[0].orderIndex").value(0))
                .andExpect(jsonPath("$.nodes[1].placeId").value(ramen.getPlaceId()))
                .andExpect(jsonPath("$.nodes[1].orderIndex").value(1))
                // then: 남은 라멘 노드는 지우지 않고 순번만 바뀜
                .andExpect(jsonPath("$.nodes[1].planNodeId").value(ramenNode.getPlanNodeId()));

        // then: DB에도 스시 노드가 없고 두 노드만 남음
        em.clear();
        List<Long> savedPlaceIds = em.createQuery(
                        "select n.place.placeId from PlanNode n where n.plan.planId = :planId order by n.orderIndex",
                        Long.class)
                .setParameter("planId", plan.getPlanId())
                .getResultList();
        assertThat(savedPlaceIds).containsExactly(udon.getPlaceId(), ramen.getPlaceId());
    }

    @Test
    @DisplayName("다른 일자에 이미 넣은 장소는 한 계획 안에서 다시 확정할 수 없다.")
    void saveDay_placeInOtherDay_conflict() throws Exception {
        // given: 2일차에 라멘이 있음
        plan.getDays().get(1).addNode(ramen, 0);
        em.flush();
        em.clear();

        // when / then: 1일차에 라멘을 넣으면 409
        saveDay(owner, 1, nodes(node(ramen)), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PLAN_PLACE"));
    }

    @Test
    @DisplayName("같은 장소를 한 요청에서 두 노드로 넣거나 자기 자신을 플랜B로 넣으면 실패한다.")
    void saveDay_duplicateInRequest_conflict() throws Exception {
        // given: 빈 1일차
        em.clear();

        // when / then: 라멘을 두 번 넣으면 409
        saveDay(owner, 1, nodes(node(ramen), node(ramen)), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PLAN_PLACE"));
        // when / then: 라멘 노드의 플랜B로 라멘을 넣어도 409
        saveDay(owner, 1, nodes(node(ramen, ramen)), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PLAN_PLACE"));
    }

    @Test
    @DisplayName("노드가 계획에서 빠지면 그 노드의 플랜B도 같이 지워진다.")
    void saveDay_removedNode_deletesAlternatives() throws Exception {
        // given: 라멘 노드에 스시 플랜B
        plan.getDays().get(0).addNode(ramen, 0).addAlternative(sushi);
        em.flush();
        em.clear();

        // when: 라멘을 빼고 우동만 저장하면
        saveDay(owner, 1, nodes(node(udon)), false)
                .andExpect(status().isOk());

        // then: 플랜B가 남지 않음
        em.clear();
        Long alternativeCount = em.createQuery("select count(a) from PlanAlternative a", Long.class)
                .getSingleResult();
        assertThat(alternativeCount).isZero();
    }

    @Test
    @DisplayName("맛집 노드에 맛집과 카페를 플랜B로 저장하고 각 장소의 카테고리를 조회한다.")
    void saveDay_acceptsAlternativesAcrossCategories() throws Exception {
        // given: 빈 1일차와 맛집·카페 카드
        em.clear();

        // when: 맛집 노드에 맛집·카페 대안을 함께 저장
        ResultActions result = saveDay(owner, 1, nodes(node(ramen, sushi, cafe)), false);

        // then: 저장 응답과 재조회에 플랜B의 실제 카테고리가 내려옴
        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].alternatives.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].alternatives[0].placeId").value(sushi.getPlaceId()))
                .andExpect(jsonPath("$.nodes[0].alternatives[0].category").value("FOOD"))
                .andExpect(jsonPath("$.nodes[0].alternatives[1].placeId").value(cafe.getPlaceId()))
                .andExpect(jsonPath("$.nodes[0].alternatives[1].category").value("CAFE"))
                .andExpect(jsonPath("$.nodes[0].alternatives[1].planAlternativeId").isNumber());

        em.flush();
        em.clear();
        mockMvc.perform(get("/plans/{planId}", plan.getPlanId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].nodes[0].alternatives[?(@.placeId == "
                        + cafe.getPlaceId() + ")].category", contains("CAFE")));
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"TRANSPORT", "TIP"})
    @DisplayName("교통·팁 카드는 노드로도 플랜B로도 넣을 수 없다.")
    void saveDay_nonPlaceCard_rejected(Category category) throws Exception {
        // given: 빈 1일차와 교통 또는 팁 카드
        Place nonPlace = placeRepository.save(PlanEditFixtures.place(owner, category, "지하철 1일권"));
        em.flush();
        em.clear();

        // when / then: 카드를 노드로 추가하면 실패
        saveDay(owner, 1, nodes(node(nonPlace)), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_PLACE"));
        // when / then: 카드를 플랜B로 추가해도 실패
        saveDay(owner, 1, nodes(node(ramen, nonPlace)), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_PLACE"));
    }

    @Test
    @DisplayName("모든 일자가 완성되면 계획이 COMPLETED가 되고, 한 일자라도 다시 미완성이 되면 DRAFT로 돌아간다.")
    void saveDay_allDaysCompleted_planCompleted() throws Exception {
        // given: 2일짜리 계획
        em.clear();

        // when: 1일차만 완성하면 아직 DRAFT
        saveDay(owner, 1, nodes(node(ramen)), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCompleted").value(true))
                .andExpect(jsonPath("$.planStatus").value("DRAFT"));
        // when: 2일차도 완성하면 COMPLETED
        saveDay(owner, 2, nodes(node(sushi)), true)
                .andExpect(jsonPath("$.planStatus").value("COMPLETED"));
        assertThat(planStatus()).isEqualTo(PlanStatus.COMPLETED);
        // when: 1일차를 다시 미완성으로 바꾸면 DRAFT
        saveDay(owner, 1, nodes(node(ramen)), false)
                .andExpect(jsonPath("$.planStatus").value("DRAFT"));
        assertThat(planStatus()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("편집 멤버가 가진 카드도 계획에 넣을 수 있고, 계획 멤버가 아닌 사람의 카드는 404로 실패한다.")
    void saveDay_placeOwnership() throws Exception {
        // given: EDITOR의 카드와 남의 카드
        Place editorPlace = placeRepository.save(PlanEditFixtures.place(editor, Category.FOOD, "모츠나베"));
        Place strangerPlace = placeRepository.save(PlanEditFixtures.place(stranger, Category.FOOD, "야타이"));
        em.flush();
        em.clear();

        // when / then: EDITOR 카드는 OWNER가 넣어도 됨
        saveDay(owner, 1, nodes(node(editorPlace)), false)
                .andExpect(status().isOk());
        // when / then: 남의 카드는 PLACE_NOT_FOUND
        saveDay(owner, 1, nodes(node(strangerPlace)), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("읽기 전용으로 공유받은 멤버가 동선을 저장하면 권한 없음(403)으로 실패한다.")
    void saveDay_viewer_forbidden() throws Exception {
        // given: VIEWER 멤버
        em.clear();
        // when / then
        saveDay(viewer, 1, nodes(node(ramen)), false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FORBIDDEN"));
    }

    @Test
    @DisplayName("다른 사람의 공유하지 않은 계획을 고치면 있다는 것도 알리지 않고 404로 실패한다.")
    void saveDay_stranger_notFound() throws Exception {
        // given: 멤버가 아닌 사람
        em.clear();
        // when / then
        saveDay(stranger, 1, nodes(node(ramen)), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("계획에 없는 일자를 저장하면 404 PLAN_DAY_NOT_FOUND로 실패한다.")
    void saveDay_missingDay_notFound() throws Exception {
        // given: 2일짜리 계획
        em.clear();
        // when / then: 3일차
        saveDay(owner, 3, nodes(node(ramen)), false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_DAY_NOT_FOUND"));
    }

    @Test
    @DisplayName("완성 여부를 빼고 보내면 입력 오류로 실패한다.")
    void saveDay_missingIsCompleted_invalidInput() throws Exception {
        // given / when / then
        mockMvc.perform(put("/plans/{planId}/days/1", plan.getPlanId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private ResultActions saveDay(User user, int dayNumber, String nodesJson, boolean isCompleted) throws Exception {
        return mockMvc.perform(put("/plans/{planId}/days/{dayNumber}", plan.getPlanId(), dayNumber)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodes\":" + nodesJson + ",\"isCompleted\":" + isCompleted + "}"));
    }

    private String nodes(String... nodes) {
        return "[" + String.join(",", nodes) + "]";
    }

    private String node(Place place, Place... alternatives) {
        String alternativeIds = String.join(",",
                java.util.Arrays.stream(alternatives).map(a -> a.getPlaceId().toString()).toList());
        return "{\"placeId\":" + place.getPlaceId() + ",\"alternativePlaceIds\":[" + alternativeIds + "]}";
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getUserId());
    }

    private PlanStatus planStatus() {
        em.clear();
        return planRepository.findById(plan.getPlanId()).orElseThrow().getStatus();
    }
}
