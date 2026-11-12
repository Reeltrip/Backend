package com.reeltrip.domain.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;

/** 소유: place 팀원. plan 쪽에서 쓰는 메서드(count·find 메서드)는 이름을 바꾸지 마세요. */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByPlaceIdAndUser_UserId(Long placeId, Long userId);

    long countByUser_UserId(Long userId);

    /** 계획 만들 때 그 도시의 장소 카드 수(loadedPlaceCount). categories에는 isPlace()인 것만 넘겨요. */
    long countByUser_UserIdAndCityAndCategoryIn(Long userId, String city, Collection<Category> categories);

    /** 계획 보기의 tips: 그 도시의 교통·팁 카드 */
    List<Place> findAllByUser_UserIdAndCityAndCategoryIn(Long userId, String city, Collection<Category> categories);

    List<Place> findAllByPlaceIdIn(Collection<Long> placeIds);
}
