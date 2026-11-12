package com.reeltrip.domain.plan.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.PlanDayArea;

/** 소유: plan-edit 팀원. 구역은 PlanDay.addArea/clearAreas로 바뀌어서 보통 직접 쓸 일은 없어요. */
public interface PlanDayAreaRepository extends JpaRepository<PlanDayArea, Long> {
}
