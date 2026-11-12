package com.reeltrip.domain.plan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.PlanMember;

/** 소유: plan 팀원. PlanAccessService(리드)가 findByPlan_PlanIdAndUser_UserId를 써요. */
public interface PlanMemberRepository extends JpaRepository<PlanMember, Long> {

    Optional<PlanMember> findByPlan_PlanIdAndUser_UserId(Long planId, Long userId);

    long countByUser_UserId(Long userId);
}
