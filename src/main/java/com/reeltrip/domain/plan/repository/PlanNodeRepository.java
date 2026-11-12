package com.reeltrip.domain.plan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.PlanNode;

/** 소유: plan-edit 팀원. place·plan 팀원이 쓰는 메서드는 이름을 바꾸지 마세요. */
public interface PlanNodeRepository extends JpaRepository<PlanNode, Long> {

    boolean existsByPlan_PlanIdAndPlace_PlaceId(Long planId, Long placeId);

    long countByPlan_PlanId(Long planId);

    /** 카드를 지울 때 그 카드를 쓰는 노드를 찾아요(place 팀원이 씀) */
    List<PlanNode> findAllByPlace_PlaceId(Long placeId);
}
