package com.reeltrip.domain.plan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.Plan;

/** 소유: plan 팀원. 목록 조회(owner·status·period·페이지) 쿼리는 plan 팀원이 추가해요. */
public interface PlanRepository extends JpaRepository<Plan, Long> {
}
