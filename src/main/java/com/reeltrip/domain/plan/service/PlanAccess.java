package com.reeltrip.domain.plan.service;

import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;

/** 계획과 부른 사람의 역할. 로그인 안 하고 공유 계획을 보는 경우 role은 null */
public record PlanAccess(Plan plan, PlanRole role) {
}
