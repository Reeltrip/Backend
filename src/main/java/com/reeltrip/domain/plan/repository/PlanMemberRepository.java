package com.reeltrip.domain.plan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reeltrip.domain.plan.entity.PlanMember;

/** 소유: plan 팀원. PlanAccessService(리드)가 findByPlan_PlanIdAndUser_UserId를 써요. */
public interface PlanMemberRepository extends JpaRepository<PlanMember, Long> {

    Optional<PlanMember> findByPlan_PlanIdAndUser_UserId(Long planId, Long userId);

    long countByUser_UserId(Long userId);

    @Query("""
            select count(m) from PlanMember m where m.user.userId=:userId and (
            exists (select n.planNodeId from PlanNode n where n.plan=m.plan and n.place.placeId=:placeId)
            or exists (select a.planAlternativeId from PlanAlternative a where a.planNode.plan=m.plan and a.place.placeId=:placeId)
            or exists (select s.planStayId from PlanStay s where s.plan=m.plan and s.place.placeId=:placeId))
            """)
    long readablePlaceReferences(@Param("userId") Long userId,@Param("placeId") Long placeId);
}
