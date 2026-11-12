package com.reeltrip.infra.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;

/**
 * GEMINI_API_KEY가 없는 로컬에서 쓰는 계획 초안. 첫 숙소 카드를 숙소로 고르고,
 * 나머지 장소를 경도(서→동) 순으로 줄 세워 일자 수만큼 고르게 나눠요. 가까운 곳이 대체로 같은 날에 모여요.
 */
public class FakePlanDraftClient implements PlanDraftClient {

    @Override
    public PlanDraftResult draftPlan(PlanDraftRequest request) {
        List<Long> stays = request.candidates().stream()
                .filter(candidate -> candidate.category() == Category.STAY)
                .map(PlanDraftRequest.Candidate::placeId)
                .limit(1)
                .toList();
        List<PlanDraftRequest.Candidate> nodes = request.candidates().stream()
                .filter(candidate -> candidate.category() != Category.STAY)
                .sorted(Comparator.comparing(PlanDraftRequest.Candidate::longitude,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit((long) request.dayCount() * PlanDraftResponseParser.MAX_NODES_PER_DAY)
                .toList();

        List<List<Long>> days = new ArrayList<>();
        int perDay = (int) Math.ceil((double) nodes.size() / request.dayCount());
        for (int day = 0; day < request.dayCount(); day++) {
            int from = Math.min(day * perDay, nodes.size());
            int to = Math.min(from + perDay, nodes.size());
            days.add(nodes.subList(from, to).stream().map(PlanDraftRequest.Candidate::placeId).toList());
        }
        return new PlanDraftResult(stays, days);
    }
}
