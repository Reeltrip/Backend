package com.reeltrip.domain.plan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.PlanAlternative;

/** 소유: plan-edit 팀원 */
public interface PlanAlternativeRepository extends JpaRepository<PlanAlternative, Long> {

    /** 카드를 지울 때 그 카드를 쓰는 플랜B를 찾아요(place 팀원이 씀) */
    List<PlanAlternative> findAllByPlace_PlaceId(Long placeId);
}
