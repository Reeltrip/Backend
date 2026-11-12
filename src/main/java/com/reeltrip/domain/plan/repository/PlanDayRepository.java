package com.reeltrip.domain.plan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.PlanDay;

/** 소유: plan 팀원. plan-edit도 findByPlan_PlanIdAndDayNumber를 써요. */
public interface PlanDayRepository extends JpaRepository<PlanDay, Long> {

    Optional<PlanDay> findByPlan_PlanIdAndDayNumber(Long planId, int dayNumber);
}
