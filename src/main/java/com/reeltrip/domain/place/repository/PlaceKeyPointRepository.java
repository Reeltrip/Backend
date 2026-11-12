package com.reeltrip.domain.place.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.place.entity.PlaceKeyPoint;

/** 소유: place 팀원. 핵심 포인트는 Place.update로 바뀌어서 보통 직접 쓸 일은 없어요. */
public interface PlaceKeyPointRepository extends JpaRepository<PlaceKeyPoint, Long> {
}
