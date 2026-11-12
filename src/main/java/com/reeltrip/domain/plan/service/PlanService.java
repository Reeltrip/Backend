package com.reeltrip.domain.plan.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.plan.dto.PlanCopyResponse;
import com.reeltrip.domain.plan.dto.PlanCreateRequest;
import com.reeltrip.domain.plan.dto.PlanCreateResponse;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.dto.PlanListRequest;
import com.reeltrip.domain.plan.dto.PlanListResponse;
import com.reeltrip.domain.plan.dto.PlanSummaryResponse;
import com.reeltrip.domain.plan.dto.PlanUpdateRequest;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;

/** 계획 만들기·목록·보기·수정·삭제·복제 (S4-0, S4-1, S4-2, S4-7, S5-1). 소유: plan 팀원 */
@Service
public class PlanService {

    /** GET /users/me의 여행목록 수: 내 계획 + 공유받은 계획 (BE 1이 씀) */
    public long countPlans(Long userId) {
        return 0; // TODO: 가짜 응답, 실제 구현으로 교체
    }

    public PlanListResponse getPlans(Long userId, PlanListRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        PlanSummaryResponse plan = new PlanSummaryResponse(7L, "오사카 3박 4일", LocalDate.of(2026, 11, 14),
                LocalDate.of(2026, 11, 17), "오사카", PlanStatus.DRAFT, 1, 4, 5,
                "https://storage.googleapis.com/fake.jpg", OffsetDateTime.parse("2026-11-12T09:00:00+09:00"),
                userId, "보영", List.of(new PlanSummaryResponse.Member(userId, "보영", PlanRole.OWNER),
                new PlanSummaryResponse.Member(4L, "민지", PlanRole.EDITOR)));
        return new PlanListResponse(List.of(plan), 1);
    }

    public PlanCreateResponse createPlan(Long userId, PlanCreateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return new PlanCreateResponse(7L, PlanStatus.DRAFT, 4, 27);
    }

    public PlanDetailResponse getPlan(Long planId, Long userId) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return fakeDetail(planId, userId == null ? null : PlanRole.OWNER);
    }

    public PlanDetailResponse modifyPlan(Long planId, Long userId, PlanUpdateRequest request) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return fakeDetail(planId, PlanRole.OWNER);
    }

    public void removePlan(Long planId, Long userId) {
        // TODO: 가짜 응답, 실제 구현으로 교체
    }

    public PlanCopyResponse copyPlan(Long planId, Long userId) {
        // TODO: 가짜 응답, 실제 구현으로 교체
        return new PlanCopyResponse(15L);
    }

    private PlanDetailResponse fakeDetail(Long planId, PlanRole role) {
        PlanDetailResponse.Node node = new PlanDetailResponse.Node(301L, 0, 101L, Category.FOOD, "이치란 라멘",
                34.668, 135.501, "https://storage.googleapis.com/fake.jpg",
                List.of(new PlanDetailResponse.Alternative(9L, 120L, "킨류 라멘")));
        PlanDetailResponse.Day day = new PlanDetailResponse.Day(21L, 1, LocalDate.of(2026, 11, 14), true,
                List.of(new PlanDetailResponse.Area("난바", 34.6659, 135.5013)), List.of(node));
        return new PlanDetailResponse(planId, "오사카 3박 4일", LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 17),
                "오사카", PlanStatus.DRAFT, role,
                List.of(new PlanDetailResponse.Stay(3L, 55L, "오리엔탈 호텔", 34.69, 135.5)),
                List.of(day),
                List.of(new PlanDetailResponse.Tip(130L, Category.TIP, "지하철 1일권은 640엔", null)));
    }
}
