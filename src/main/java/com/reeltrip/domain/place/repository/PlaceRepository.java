package com.reeltrip.domain.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.service.CityNames;

/** 소유: place 팀원. plan 쪽에서 쓰는 메서드(count·find 메서드)는 이름을 바꾸지 마세요. */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByPlaceIdAndUser_UserId(Long placeId, Long userId);

    long countByUser_UserId(Long userId);

    List<Place> findAllByUser_UserIdAndCategoryIn(Long userId, Collection<Category> categories);

    /**
     * 그 도시의 카드. 계획 만들 때 장소 카드 수(loadedPlaceCount), AI 초안 후보, 계획 보기 tips에 써요.
     * 계획 도시("오사카시")와 카드 도시("오사카")는 글자가 달라서 끝의 행정 단위를 떼고 비교해요 (#97)
     */
    default List<Place> findAllInSameCity(Long userId, String city, Collection<Category> categories) {
        return findAllByUser_UserIdAndCategoryIn(userId, categories).stream()
                .filter(place -> CityNames.isSame(place.getCity(), city))
                .toList();
    }

    List<Place> findAllByPlaceIdIn(Collection<Long> placeIds);

    /** GET /places: 반경·카테고리 필터와 페이지 자르기는 서비스가 메모리에서 해요(해커톤 규모) */
    List<Place> findAllByUser_UserIdOrderByCreatedAtDescPlaceIdDesc(Long userId);

    /** #87 저장소로 옮길 사진을 고를 때 써요. 같은 사진을 쓰는 카드가 많아서 주소만 겹치지 않게 가져와요. */
    @Query("select distinct p.imageUrl from Place p where p.imageUrl is not null")
    List<String> findDistinctImageUrls();

    /** #87 같은 사진을 쓰는 카드의 주소를 한 번에 바꿔요. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Place p set p.imageUrl = :to where p.imageUrl = :from")
    int replaceImageUrl(@Param("from") String from, @Param("to") String to);
}
