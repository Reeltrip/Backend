package com.reeltrip.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserMeApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PlaceRepository places;
    @Autowired PlanRepository plans;
    @Autowired JwtProvider jwt;

    User member;
    User other;

    @BeforeEach
    void setUp() {
        member = users.save(TestFixtures.user("me-api@example.test"));
        other = users.save(TestFixtures.user("other-me-api@example.test"));
    }

    @Test
    @DisplayName("로그인하지 않으면 내 정보를 조회할 수 없다.")
    void unauthenticatedRequestRejected() throws Exception {
        // given / when / then
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("잘못된 토큰으로 내 정보를 조회할 수 없다.")
    void invalidTokenRejected() throws Exception {
        // given / when / then
        mvc.perform(get("/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("토큰에 적힌 회원이 DB에 없으면 다시 로그인이 필요하다.")
    void missingMemberRejected() throws Exception {
        // given: 서명은 맞지만 존재하지 않는 회원
        String token = jwt.createAccessToken(Long.MAX_VALUE);
        // when / then
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("카드와 계획이 없으면 0과 명세의 다섯 필드만 반환한다.")
    void emptyProfileHasExactContract() throws Exception {
        // given / when
        String body = mvc.perform(get("/users/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(member.getUserId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()))
                .andExpect(jsonPath("$.nickname").value(member.getNickname()))
                .andExpect(jsonPath("$.placeCount").value(0)).andExpect(jsonPath("$.planCount").value(0))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        // then
        assertThat(mapper.readTree(body).size()).isEqualTo(5);
    }

    @Test
    @DisplayName("내 장소·팁 카드와 OWNER·EDITOR·VIEWER 계획만 세고 다른 회원 데이터는 빼준다.")
    void profileCountsOnlyOwnCardsAndJoinedPlans() throws Exception {
        // given
        places.save(Place.create(member, null, TestFixtures.foodContent("내 장소", "후쿠오카")));
        places.save(Place.create(member, null, new PlaceContent(Category.TIP, "환전", null, null, null, null,
                "후쿠오카", null, null, null, List.of(), null, null, null, null, null,
                "환전", "후쿠오카", null, null, null, null, null)));
        places.save(Place.create(other, null, TestFixtures.foodContent("다른 회원 장소", "후쿠오카")));
        savePlan(member, "내 여행", null);
        savePlan(other, "편집 여행", PlanRole.EDITOR);
        savePlan(other, "읽기 여행", PlanRole.VIEWER);
        savePlan(other, "무관한 여행", null);
        places.flush();
        plans.flush();
        // when / then
        mvc.perform(get("/users/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.planCount").value(3));
    }

    @Test
    @DisplayName("쿼리에 다른 회원 ID를 넣어도 로그인한 회원의 정보만 반환한다.")
    void requestCannotChooseAnotherMember() throws Exception {
        // given / when / then
        mvc.perform(get("/users/me").param("userId", other.getUserId().toString())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(member.getUserId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()));
    }

    private void savePlan(User owner, String name, PlanRole memberRole) {
        Plan plan = Plan.create(owner, name, TestFixtures.date(11, 20), TestFixtures.date(11, 21),
                "후쿠오카", null, null, null);
        plan.addMember(owner, PlanRole.OWNER);
        if (memberRole != null) {
            plan.addMember(member, memberRole);
        }
        plans.save(plan);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getUserId());
    }
}
