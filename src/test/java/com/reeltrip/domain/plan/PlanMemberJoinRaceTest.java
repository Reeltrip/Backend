package com.reeltrip.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.ShareType;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.support.TestFixtures;

/**
 * 결정 #7 자동 멤버 추가의 동시 요청. 실제 커밋이 필요해서 @Transactional 없이 돌리고 끝나면 지워요.
 * 같은 사람이 공유 링크를 동시에 열어도(FE 중복 호출 등) 모두 200이고 멤버는 한 번만 생겨야 해요.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlanMemberJoinRaceTest {

    private static final int REQUESTS = 8;

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired PlanRepository planRepository;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> planIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long planId : planIds) {
            jdbc.update("DELETE FROM plan_member WHERE plan_id = ?", planId);
            jdbc.update("DELETE FROM plan_day WHERE plan_id = ?", planId);
            jdbc.update("DELETE FROM plan WHERE plan_id = ?", planId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM user WHERE user_id = ?", userId);
        }
    }

    @Test
    @DisplayName("같은 사람이 공유 계획을 동시에 여러 번 열어도 모두 열리고 멤버는 한 번만 추가된다.")
    void openSharedPlanConcurrently_joinsOnce() throws Exception {
        for (int round = 0; round < 3; round++) {
            // given: 함께 편집으로 공유한 계획과 아직 멤버가 아닌 회원
            User owner = save(TestFixtures.user("race-owner-" + round + "@test.com"));
            User guest = save(TestFixtures.user("race-guest-" + round + "@test.com"));
            Plan plan = PlanEditFixtures.plan(owner, 2);
            plan.changeShareType(ShareType.EDIT);
            Long planId = planRepository.save(plan).getPlanId();
            planIds.add(planId);
            String token = "Bearer " + jwtProvider.createAccessToken(guest.getUserId());

            // when: 같은 순간에 8번 열기
            List<Integer> statuses = openAtOnce(planId, token);

            // then
            assertThat(statuses).as("round %d", round).containsOnly(200);
            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM plan_member WHERE plan_id = ? AND user_id = ?", Integer.class,
                    planId, guest.getUserId());
            assertThat(rows).isEqualTo(1);
            assertThat(open(planId, token)).isEqualTo(200);
        }
    }

    private User save(User user) {
        User saved = userRepository.save(user);
        userIds.add(saved.getUserId());
        return saved;
    }

    private List<Integer> openAtOnce(Long planId, String token) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return open(planId, token);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int open(Long planId, String token) throws Exception {
        return mockMvc.perform(get("/plans/{planId}", planId).header("Authorization", token))
                .andReturn().getResponse().getStatus();
    }
}
