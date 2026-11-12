package com.reeltrip.domain.plan.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.global.config.TimeConfig;

/** GET /plans 목록 한 칸 (S4-0 계획 홈, S5-1 여행목록). 진행률은 completedDayCount / totalDayCount */
public record PlanSummaryResponse(
        Long planId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String city,
        PlanStatus status,
        int completedDayCount,
        int totalDayCount,
        int placeCount,
        String coverImageUrl,
        OffsetDateTime createdAt,
        Long ownerUserId,
        String ownerNickname,
        List<Member> members) {

    /** OWNER 포함 멤버 (Figma 여행목록 멤버 아바타) */
    public record Member(Long userId, String nickname, PlanRole role) {
    }

    public static PlanSummaryResponse from(Plan plan) {
        List<PlanDay> days = plan.getDays().stream()
                .sorted(Comparator.comparingInt(PlanDay::getDayNumber))
                .toList();
        int completedDayCount = (int) days.stream().filter(PlanDay::isCompleted).count();
        int placeCount = days.stream().mapToInt(day -> day.getNodes().size()).sum();
        List<Member> members = plan.getMembers().stream()
                .map(member -> new Member(member.getUser().getUserId(), member.getUser().getNickname(),
                        member.getRole()))
                .toList();
        return new PlanSummaryResponse(
                plan.getPlanId(),
                plan.getName(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getCity(),
                plan.getStatus(),
                completedDayCount,
                days.size(),
                placeCount,
                coverImageUrl(days),
                plan.getCreatedAt() == null ? null
                        : plan.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS),
                plan.getUser().getUserId(),
                plan.getUser().getNickname(),
                members);
    }

    /** 노드가 있는 가장 앞 일자에서 orderIndex가 가장 작은 노드의 카드 사진. 노드가 없으면 null */
    private static String coverImageUrl(List<PlanDay> days) {
        return days.stream()
                .map(day -> day.getNodes().stream().min(Comparator.comparingInt(PlanNode::getOrderIndex)))
                .flatMap(Optional::stream)
                .findFirst()
                .map(node -> node.getPlace().getImageUrl())
                .orElse(null);
    }
}
