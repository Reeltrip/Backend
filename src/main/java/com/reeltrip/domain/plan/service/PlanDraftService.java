package com.reeltrip.domain.plan.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.entity.PlanStay;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.ai.PlanDraftClient;
import com.reeltrip.infra.ai.PlanDraftRequest;
import com.reeltrip.infra.ai.PlanDraftResult;

import jakarta.persistence.EntityManager;

/**
 * S4-2 AI 초안. 1단계(여행명·장소·기간)로 만든 계획에 AI가 숙소와 일자별 노드 순서를 채워요.
 * 후보는 계획 만든 사람의 보관함에서 계획 도시의 장소 카드예요(계획 만들 때 불러오는 카드와 같음).
 * 기존 노드·플랜B는 덮어쓰고, 모든 일자는 완성·계획은 COMPLETED가 돼요(#60, 바로 계획 완성 보기로 감).
 * 숙소는 사용자가 이미 정해 두었으면(예약한 숙소) 그대로 두고, 비어 있을 때만 AI가 숙소 카드 중에서 골라요.
 */
@Service
public class PlanDraftService {

    /** LLM에 넘기는 후보 최대 수. 프롬프트가 끝없이 길어지지 않게 최근 카드부터 잘라요. */
    static final int MAX_CANDIDATES = 60;
    private static final List<Category> PLACE_CATEGORIES =
            Arrays.stream(Category.values()).filter(Category::isPlace).toList();
    private static final List<Category> TIP_CATEGORIES = List.of(Category.TRANSPORT, Category.TIP);

    private final PlanAccessService planAccessService;
    private final PlanRepository planRepository;
    private final PlaceRepository placeRepository;
    private final PlanDraftClient planDraftClient;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public PlanDraftService(PlanAccessService planAccessService, PlanRepository planRepository,
                            PlaceRepository placeRepository, PlanDraftClient planDraftClient,
                            TransactionTemplate transactionTemplate, EntityManager entityManager) {
        this.planAccessService = planAccessService;
        this.planRepository = planRepository;
        this.placeRepository = placeRepository;
        this.planDraftClient = planDraftClient;
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
    }

    /**
     * LLM 호출(최대 수십 초)은 DB 트랜잭션 밖에서 해요. 그동안 DB 연결을 잡고 있지 않으려고
     * 후보 읽기와 결과 저장을 따로 묶었어요. 권한은 저장할 때 한 번 더 확인해요.
     */
    public PlanDetailResponse createAiDraft(Long planId, Long userId) {
        PlanDraftRequest request = transactionTemplate.execute(status -> prepare(planId, userId));
        PlanDraftResult result = planDraftClient.draftPlan(request);
        return transactionTemplate.execute(status -> apply(planId, userId, result));
    }

    private PlanDraftRequest prepare(Long planId, Long userId) {
        Plan plan = planAccessService.getEditablePlan(planId, userId).plan();
        // 사용자가 미리 정한(예약한) 숙소가 있으면 그 숙소만 숙소 후보로 넘기고, 다른 숙소 카드는 빼요.
        List<Place> chosenStays = plan.getStays().stream().map(PlanStay::getPlace).toList();
        boolean hasChosenStays = !chosenStays.isEmpty();
        List<PlanDraftRequest.Candidate> candidates = new ArrayList<>(placeRepository
                .findAllInSameCity(plan.getUser().getUserId(), plan.getCity(), PLACE_CATEGORIES)
                .stream()
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .filter(place -> !hasChosenStays || place.getCategory() != Category.STAY)
                .sorted(Comparator.comparing(Place::getPlaceId).reversed())
                .limit(MAX_CANDIDATES)
                .map(PlanDraftService::toCandidate)
                .toList());
        boolean hasNodeCandidate = candidates.stream().anyMatch(c -> c.category() != Category.STAY);
        if (!hasNodeCandidate) {
            throw new BusinessException(ErrorCode.AI_DRAFT_NO_PLACES);
        }
        chosenStays.stream().map(PlanDraftService::toCandidate).forEach(candidates::add);
        List<Long> fixedStayPlaceIds = chosenStays.stream().map(Place::getPlaceId).toList();
        return new PlanDraftRequest(plan.getCity(), plan.getDays().size(), candidates, fixedStayPlaceIds);
    }

    private PlanDetailResponse apply(Long planId, Long userId, PlanDraftResult result) {
        PlanAccess access = planAccessService.getEditablePlan(planId, userId);
        Plan plan = access.plan();

        // 사용자가 정해 둔 숙소는 그대로 두고, 숙소가 비어 있을 때만 AI가 고른 숙소를 넣어요.
        boolean keepsStays = !plan.getStays().isEmpty();

        // plan_node·plan_stay는 (plan_id, place_id) 유니크라, 지운 것을 먼저 DB에 반영한 뒤 새로 넣어요.
        // (Hibernate는 한 번에 보내면 insert를 delete보다 먼저 보내요)
        for (PlanDay day : plan.getDays()) {
            for (PlanNode node : new ArrayList<>(day.getNodes())) {
                day.removeNode(node);
            }
            day.changeCompleted(false);
        }
        entityManager.flush();

        List<Long> stayPlaceIds = keepsStays ? List.of() : result.stayPlaceIds();
        List<Long> placeIds = new ArrayList<>(stayPlaceIds);
        result.days().forEach(placeIds::addAll);
        Map<Long, Place> places = placeRepository.findAllByPlaceIdIn(placeIds).stream()
                .collect(Collectors.toMap(Place::getPlaceId, Function.identity()));

        for (Long placeId : stayPlaceIds) {
            Place place = places.get(placeId);
            if (place != null) {
                plan.addStay(place);
            }
        }
        List<PlanDay> days = plan.getDays().stream().sorted(Comparator.comparingInt(PlanDay::getDayNumber)).toList();
        for (int i = 0; i < days.size() && i < result.days().size(); i++) {
            int orderIndex = 0;
            for (Long placeId : result.days().get(i)) {
                Place place = places.get(placeId);
                if (place != null) {
                    days.get(i).addNode(place, orderIndex++);
                }
            }
        }
        // AI 초안은 바로 S4-7 계획 완성 보기로 가요(Notion 4-4, 사용자 결정 #60). 일자를 미완성으로 고치면 다시 DRAFT예요.
        days.forEach(day -> day.changeCompleted(true));
        plan.changeStatus(PlanStatus.COMPLETED);
        planRepository.flush();

        List<Place> tips = placeRepository.findAllInSameCity(
                plan.getUser().getUserId(), plan.getCity(), TIP_CATEGORIES);
        return PlanDetailResponse.of(plan, access.role(), tips);
    }

    private static PlanDraftRequest.Candidate toCandidate(Place place) {
        return new PlanDraftRequest.Candidate(place.getPlaceId(), place.getCategory(), place.getName(),
                toDouble(place.getLatitude()), toDouble(place.getLongitude()), place.getKeyPointContents());
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
