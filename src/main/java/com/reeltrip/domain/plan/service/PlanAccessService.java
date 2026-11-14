package com.reeltrip.domain.plan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanMember;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.ShareType;
import com.reeltrip.domain.plan.repository.PlanMemberRepository;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/**
 * 계획 권한 확인 (리드 소유, plan·plan-edit 팀원이 같이 써요). docs/decisions.md #2·#7.
 * - 멤버가 아니고 공유도 꺼진 계획: 404 PLAN_NOT_FOUND (있다는 것도 알려주지 않음)
 * - 공유받았지만 권한 밖 동작: 403 PLAN_FORBIDDEN
 */
@Service
public class PlanAccessService {
    /** 기존 계획 읽기와 같이 등록된 멤버에게 노드·플랜B·숙소 카드의 좌표 읽기를 허용해요. */
    @Transactional(readOnly=true)
    public boolean canReadPlace(Long userId,Long placeId) {
        return planMemberRepository.readablePlaceReferences(userId,placeId)>0;
    }

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final UserRepository userRepository;

    public PlanAccessService(PlanRepository planRepository, PlanMemberRepository planMemberRepository,
                             UserRepository userRepository) {
        this.planRepository = planRepository;
        this.planMemberRepository = planMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * 계획 보기(GET /plans/{planId}). userId는 로그인 안 했으면 null.
     * 공유가 켜진 계획을 멤버가 아닌 로그인 사용자가 열면 READ_ONLY→VIEWER, EDIT→EDITOR로 멤버에 넣어요.
     */
    @Transactional
    public PlanAccess getReadablePlan(Long planId, Long userId) {
        Plan plan = findPlan(planId);
        if (userId == null) {
            if (!plan.isShared()) {
                throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
            }
            return new PlanAccess(plan, null);
        }
        PlanMember member = planMemberRepository.findByPlan_PlanIdAndUser_UserId(planId, userId).orElse(null);
        if (member != null) {
            return new PlanAccess(plan, member.getRole());
        }
        if (!plan.isShared()) {
            throw new BusinessException(ErrorCode.PLAN_NOT_FOUND);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        PlanRole role = plan.getShareType() == ShareType.EDIT ? PlanRole.EDITOR : PlanRole.VIEWER;
        plan.addMember(user, role);
        return new PlanAccess(plan, role);
    }

    /** 계획 고치기(일자·숙소·구역·노드·계획 정보). OWNER·EDITOR만 */
    @Transactional(readOnly = true)
    public PlanAccess getEditablePlan(Long planId, Long userId) {
        PlanAccess access = getMemberAccess(planId, userId);
        if (access.role() == PlanRole.VIEWER) {
            throw new BusinessException(ErrorCode.PLAN_FORBIDDEN);
        }
        return access;
    }

    /** 계획 삭제·공유 링크 발급. OWNER만 */
    @Transactional(readOnly = true)
    public PlanAccess getOwnedPlan(Long planId, Long userId) {
        PlanAccess access = getMemberAccess(planId, userId);
        if (access.role() != PlanRole.OWNER) {
            throw new BusinessException(ErrorCode.PLAN_FORBIDDEN);
        }
        return access;
    }

    /** 멤버가 아니면: 공유가 켜졌으면 403, 꺼졌으면 404 */
    private PlanAccess getMemberAccess(Long planId, Long userId) {
        Plan plan = findPlan(planId);
        return planMemberRepository.findByPlan_PlanIdAndUser_UserId(planId, userId)
                .map(member -> new PlanAccess(plan, member.getRole()))
                .orElseThrow(() -> new BusinessException(
                        plan.isShared() ? ErrorCode.PLAN_FORBIDDEN : ErrorCode.PLAN_NOT_FOUND));
    }

    private Plan findPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }
}
