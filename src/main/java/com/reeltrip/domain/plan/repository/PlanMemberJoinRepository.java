package com.reeltrip.domain.plan.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.reeltrip.domain.plan.entity.PlanMember;

/**
 * 공유 링크로 들어온 사람을 멤버로 넣어요(결정 #7). 같은 사람이 동시에 열면 "없음 확인 → 추가"가 겹쳐서
 * 유니크(plan_id, user_id)에 걸려 500이 났어요(#56). 그래서 DB에서 "없을 때만 추가"해요.
 */
public interface PlanMemberJoinRepository extends Repository<PlanMember, Long> {

    /** 이미 있으면 조용히 넘어가요. 0이면 다른 요청이 먼저 넣은 것 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO plan_member (plan_id, user_id, role, created_at) "
            + "VALUES (:planId, :userId, :role, :createdAt)", nativeQuery = true)
    int insertIfAbsent(@Param("planId") Long planId, @Param("userId") Long userId, @Param("role") String role,
                       @Param("createdAt") LocalDateTime createdAt);
}
