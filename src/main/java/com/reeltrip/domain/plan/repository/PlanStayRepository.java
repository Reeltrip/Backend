package com.reeltrip.domain.plan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.plan.entity.PlanStay;

/** 소유: plan-edit 팀원 */
public interface PlanStayRepository extends JpaRepository<PlanStay, Long> {

    /** 카드를 지울 때 그 카드를 쓰는 숙소 선택을 찾아요(place 팀원이 씀) */
    List<PlanStay> findAllByPlace_PlaceId(Long placeId);
}
