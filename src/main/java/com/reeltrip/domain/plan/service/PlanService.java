package com.reeltrip.domain.plan.service;

import org.springframework.stereotype.Service;

/** 계획 만들기·목록·보기·수정·삭제·복제 (S4-0, S4-1, S4-2, S4-7, S5-1). 소유: plan 팀원 */
@Service
public class PlanService {

    /** GET /users/me의 여행목록 수: 내 계획 + 공유받은 계획 (BE 1이 씀) */
    public long countPlans(Long userId) {
        return 0; // TODO: 가짜 응답, 실제 구현으로 교체
    }
}
