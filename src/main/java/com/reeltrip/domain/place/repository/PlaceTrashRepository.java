package com.reeltrip.domain.place.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.reeltrip.domain.place.entity.Place;

/**
 * 요약카드 휴지통 (#58). Place는 @SQLRestriction으로 휴지통 카드를 빼고 조회해서,
 * 휴지통 카드는 이 SQL로만 직접 다뤄요. 모두 회원 본인 카드만 대상이에요.
 */
public interface PlaceTrashRepository extends Repository<Place, Long> {

    /** 휴지통 목록. 최근에 지운 카드부터 */
    @Query(value = "SELECT * FROM place WHERE user_id = :userId AND deleted_at IS NOT NULL "
            + "ORDER BY deleted_at DESC, place_id DESC", nativeQuery = true)
    List<Place> findTrashedByUserId(@Param("userId") Long userId);

    /** placeIds 중 내 휴지통에 있는 카드 수. 요청 수와 다르면 내 휴지통에 없는 카드가 섞인 것 */
    @Query(value = "SELECT COUNT(*) FROM place WHERE user_id = :userId AND place_id IN (:placeIds) "
            + "AND deleted_at IS NOT NULL", nativeQuery = true)
    long countTrashed(@Param("userId") Long userId, @Param("placeIds") Collection<Long> placeIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE place SET deleted_at = NULL, updated_at = :now WHERE user_id = :userId "
            + "AND place_id IN (:placeIds) AND deleted_at IS NOT NULL", nativeQuery = true)
    int restore(@Param("userId") Long userId, @Param("placeIds") Collection<Long> placeIds,
                @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM place_key_point WHERE place_id IN (SELECT place_id FROM place WHERE user_id = :userId "
            + "AND place_id IN (:placeIds) AND deleted_at IS NOT NULL)", nativeQuery = true)
    int deleteKeyPointsOfTrashed(@Param("userId") Long userId, @Param("placeIds") Collection<Long> placeIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM place WHERE user_id = :userId AND place_id IN (:placeIds) "
            + "AND deleted_at IS NOT NULL", nativeQuery = true)
    int deleteTrashed(@Param("userId") Long userId, @Param("placeIds") Collection<Long> placeIds);
}
