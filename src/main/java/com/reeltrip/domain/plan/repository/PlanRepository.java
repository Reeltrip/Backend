package com.reeltrip.domain.plan.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanMember;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

/** 소유: plan 팀원. 목록 조회(owner·status·period·페이지) 쿼리는 plan 팀원이 추가해요. */
public interface PlanRepository extends JpaRepository<Plan, Long>, JpaSpecificationExecutor<Plan> {

    /**
     * GET /plans 목록. userId가 roles 중 하나로 멤버인 계획만 찾아요.
     * status·endDateFrom(종료일 ≥)·endDateBefore(종료일 <)는 null이면 거르지 않아요.
     * (plan_id, user_id)가 유니크라 멤버 조인으로 계획이 겹쳐 나오지 않아요.
     */
    default Page<Plan> findAllByMember(Long userId, Collection<PlanRole> roles, PlanStatus status,
                                       LocalDate endDateFrom, LocalDate endDateBefore, Pageable pageable) {
        Specification<Plan> spec = (root, query, cb) -> {
            Join<Plan, PlanMember> member = root.join("members");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(member.get("user").get("userId"), userId));
            predicates.add(member.get("role").in(roles));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (endDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), endDateFrom));
            }
            if (endDateBefore != null) {
                predicates.add(cb.lessThan(root.get("endDate"), endDateBefore));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return findAll(spec, pageable);
    }
}
