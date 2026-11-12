package com.reeltrip.domain.plan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.dto.PlanAreasResponse;
import com.reeltrip.domain.plan.dto.PlanAreasUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanDayOrderRequest;
import com.reeltrip.domain.plan.dto.PlanDayOrderResponse;
import com.reeltrip.domain.plan.dto.PlanDayResponse;
import com.reeltrip.domain.plan.dto.PlanDayUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanStaysResponse;
import com.reeltrip.domain.plan.dto.PlanStaysUpdateRequest;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanAlternative;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.entity.PlanStay;
import com.reeltrip.domain.plan.repository.PlanDayRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/** 숙소·구역·일자 저장(노드·플랜B)·일자 순서 (S4-3, S4-4, S4-5, S4-7). 소유: plan-edit 팀원 */
@Service
public class PlanEditService {

    /** plan_day_area 좌표 컬럼 소수 자릿수 */
    private static final int COORDINATE_SCALE = 7;

    private final PlanAccessService planAccessService;
    private final PlanDayRepository planDayRepository;
    private final PlaceRepository placeRepository;
    private final EntityManager entityManager;

    public PlanEditService(PlanAccessService planAccessService, PlanDayRepository planDayRepository,
                           PlaceRepository placeRepository, EntityManager entityManager) {
        this.planAccessService = planAccessService;
        this.planDayRepository = planDayRepository;
        this.placeRepository = placeRepository;
        this.entityManager = entityManager;
    }

    /** S4-3 숙소 선택. 통째로 바꾸고, 빈 목록이면 숙소 없음 */
    @Transactional
    public PlanStaysResponse replaceStays(Long planId, Long userId, PlanStaysUpdateRequest request) {
        Plan plan = planAccessService.getEditablePlan(planId, userId).plan();
        List<Long> placeIds = request.placeIds();
        if (new HashSet<>(placeIds).size() != placeIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Map<Long, Place> places = loadAllowedPlaces(plan, placeIds);
        for (Long placeId : placeIds) {
            if (places.get(placeId).getCategory() != Category.STAY) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_PLACE);
            }
        }

        // (plan_id, place_id) 유니크라 남는 숙소는 지우지 않고 그대로 둬요
        Set<Long> requested = Set.copyOf(placeIds);
        for (PlanStay stay : List.copyOf(plan.getStays())) {
            if (!requested.contains(stay.getPlace().getPlaceId())) {
                plan.removeStay(stay);
            }
        }
        Set<Long> kept = plan.getStays().stream()
                .map(stay -> stay.getPlace().getPlaceId())
                .collect(Collectors.toSet());
        for (Long placeId : placeIds) {
            if (!kept.contains(placeId)) {
                plan.addStay(places.get(placeId));
            }
        }
        entityManager.flush();
        return PlanStaysResponse.from(plan.getStays());
    }

    /** S4-4 구역 선택. 그 날의 구역을 통째로 바꿔요 */
    @Transactional
    public PlanAreasResponse replaceAreas(Long planId, int dayNumber, Long userId, PlanAreasUpdateRequest request) {
        planAccessService.getEditablePlan(planId, userId);
        PlanDay day = findDay(planId, dayNumber);
        day.clearAreas();
        for (PlanAreasUpdateRequest.Area area : request.areas()) {
            day.addArea(area.name().strip(), toCoordinate(area.latitude()), toCoordinate(area.longitude()));
        }
        return PlanAreasResponse.from(day);
    }

    /**
     * S4-5 동선 짜기. 그 날의 노드를 통째로 받아 배열 순서대로 orderIndex를 다시 매겨요.
     * 저장 뒤 모든 일자가 완성이면 계획을 COMPLETED, 아니면 DRAFT로 바꿔요.
     */
    @Transactional
    public PlanDayResponse saveDay(Long planId, int dayNumber, Long userId, PlanDayUpdateRequest request) {
        Plan plan = planAccessService.getEditablePlan(planId, userId).plan();
        PlanDay day = findDay(planId, dayNumber);
        List<PlanDayUpdateRequest.Node> nodeRequests = request.nodes();

        validateNoDuplicateInRequest(nodeRequests);
        Set<Long> allPlaceIds = new LinkedHashSet<>();
        for (PlanDayUpdateRequest.Node nodeRequest : nodeRequests) {
            allPlaceIds.add(nodeRequest.placeId());
            allPlaceIds.addAll(nodeRequest.alternativePlaceIdsOrEmpty());
        }
        Map<Long, Place> places = loadAllowedPlaces(plan, allPlaceIds);
        validatePlaceKinds(nodeRequests, places);
        validateNotInOtherDays(plan, day, nodeRequests);

        applyNodes(day, nodeRequests, places);
        day.changeCompleted(request.isCompleted());
        PlanStatus status = refreshStatus(plan);
        entityManager.flush();
        return PlanDayResponse.of(day, status);
    }

    /**
     * S4-7 일자 순서 바꾸기. dayNumbers의 i번째(0부터)에 적힌 기존 일자가 새 (i+1)일차가 되고
     * 날짜도 시작일 + i로 바뀌어요. 구역·노드·완성 여부는 일자를 따라가요.
     */
    @Transactional
    public PlanDayOrderResponse reorderDays(Long planId, Long userId, PlanDayOrderRequest request) {
        Plan plan = planAccessService.getEditablePlan(planId, userId).plan();
        List<PlanDay> days = List.copyOf(plan.getDays());
        List<Integer> order = request.dayNumbers();
        Set<Integer> expected = IntStream.rangeClosed(1, days.size()).boxed().collect(Collectors.toSet());
        if (order.size() != days.size() || !new HashSet<>(order).equals(expected)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Map<Integer, PlanDay> dayByNumber = days.stream()
                .collect(Collectors.toMap(PlanDay::getDayNumber, Function.identity()));
        // (plan_id, day_number) 유니크라 한 번에 바꾸면 중간에 번호가 겹쳐요. 음수 임시 번호로 먼저 옮겨요
        for (PlanDay day : days) {
            day.changeDayNumber(-day.getDayNumber());
        }
        entityManager.flush();
        for (int i = 0; i < order.size(); i++) {
            PlanDay day = dayByNumber.get(order.get(i));
            day.changeDayNumber(i + 1);
            day.changeDate(plan.getStartDate().plusDays(i));
        }
        entityManager.flush();

        return new PlanDayOrderResponse(days.stream()
                .sorted(Comparator.comparingInt(PlanDay::getDayNumber))
                .map(PlanDayOrderResponse.Day::from)
                .toList());
    }

    private PlanDay findDay(Long planId, int dayNumber) {
        return planDayRepository.findByPlan_PlanIdAndDayNumber(planId, dayNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_DAY_NOT_FOUND));
    }

    /**
     * 계획에 넣을 수 있는 카드만 불러와요(docs/decisions.md #8): 그 계획 OWNER·EDITOR의 카드이거나
     * 이미 그 계획(노드·플랜B·숙소)에 있던 카드. 아니면 없는 카드와 똑같이 404예요.
     */
    private Map<Long, Place> loadAllowedPlaces(Plan plan, Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> editorUserIds = plan.getMembers().stream()
                .filter(member -> member.getRole() != PlanRole.VIEWER)
                .map(member -> member.getUser().getUserId())
                .collect(Collectors.toSet());
        Set<Long> placeIdsInPlan = findPlaceIdsInPlan(plan);
        Map<Long, Place> places = placeRepository.findAllByPlaceIdIn(placeIds).stream()
                .collect(Collectors.toMap(Place::getPlaceId, Function.identity()));
        for (Long placeId : placeIds) {
            Place place = places.get(placeId);
            boolean isAllowed = place != null
                    && (editorUserIds.contains(place.getUser().getUserId()) || placeIdsInPlan.contains(placeId));
            if (!isAllowed) {
                throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
            }
        }
        return places;
    }

    private Set<Long> findPlaceIdsInPlan(Plan plan) {
        Set<Long> placeIds = new HashSet<>();
        plan.getStays().forEach(stay -> placeIds.add(stay.getPlace().getPlaceId()));
        for (PlanDay day : plan.getDays()) {
            for (PlanNode node : day.getNodes()) {
                placeIds.add(node.getPlace().getPlaceId());
                node.getAlternatives().forEach(alternative -> placeIds.add(alternative.getPlace().getPlaceId()));
            }
        }
        return placeIds;
    }

    /** 요청 안에서 같은 장소가 두 노드에 나오거나, 노드 자신·같은 카드를 플랜B로 두 번 넣으면 409 */
    private void validateNoDuplicateInRequest(List<PlanDayUpdateRequest.Node> nodeRequests) {
        Set<Long> nodePlaceIds = new HashSet<>();
        for (PlanDayUpdateRequest.Node nodeRequest : nodeRequests) {
            if (!nodePlaceIds.add(nodeRequest.placeId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_PLAN_PLACE);
            }
            Set<Long> alternativeIds = new HashSet<>();
            for (Long alternativeId : nodeRequest.alternativePlaceIdsOrEmpty()) {
                if (alternativeId.equals(nodeRequest.placeId()) || !alternativeIds.add(alternativeId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_PLAN_PLACE);
                }
            }
        }
    }

    /** 교통·팁은 노드·플랜B에 못 넣고, 플랜B는 대신할 노드와 같은 카테고리만(decisions #3) */
    private void validatePlaceKinds(List<PlanDayUpdateRequest.Node> nodeRequests, Map<Long, Place> places) {
        for (PlanDayUpdateRequest.Node nodeRequest : nodeRequests) {
            Category nodeCategory = places.get(nodeRequest.placeId()).getCategory();
            if (!nodeCategory.isPlace()) {
                throw new BusinessException(ErrorCode.INVALID_PLAN_PLACE);
            }
            for (Long alternativeId : nodeRequest.alternativePlaceIdsOrEmpty()) {
                if (places.get(alternativeId).getCategory() != nodeCategory) {
                    throw new BusinessException(ErrorCode.INVALID_PLAN_PLACE);
                }
            }
        }
    }

    /** S4-5c-3: 다른 일자에 이미 넣은 장소는 다시 확정할 수 없어요 */
    private void validateNotInOtherDays(Plan plan, PlanDay day, List<PlanDayUpdateRequest.Node> nodeRequests) {
        Set<Long> otherDayPlaceIds = plan.getDays().stream()
                .filter(other -> !other.getPlanDayId().equals(day.getPlanDayId()))
                .flatMap(other -> other.getNodes().stream())
                .map(node -> node.getPlace().getPlaceId())
                .collect(Collectors.toSet());
        for (PlanDayUpdateRequest.Node nodeRequest : nodeRequests) {
            if (otherDayPlaceIds.contains(nodeRequest.placeId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_PLAN_PLACE);
            }
        }
    }

    /**
     * Hibernate는 insert를 delete보다 먼저 보내서, 같은 장소 노드를 지웠다 다시 넣으면 (plan_id, place_id) 유니크에 걸려요.
     * 그래서 남는 장소의 노드는 지우지 않고 orderIndex만 고쳐요. 빠진 노드는 removeNode로 지워 플랜B도 같이 지워져요.
     */
    private void applyNodes(PlanDay day, List<PlanDayUpdateRequest.Node> nodeRequests, Map<Long, Place> places) {
        Set<Long> requestedPlaceIds = nodeRequests.stream()
                .map(PlanDayUpdateRequest.Node::placeId)
                .collect(Collectors.toSet());
        Map<Long, PlanNode> keptNodes = new HashMap<>();
        for (PlanNode node : List.copyOf(day.getNodes())) {
            Long placeId = node.getPlace().getPlaceId();
            if (requestedPlaceIds.contains(placeId)) {
                keptNodes.put(placeId, node);
            } else {
                day.removeNode(node);
            }
        }
        for (int i = 0; i < nodeRequests.size(); i++) {
            PlanDayUpdateRequest.Node nodeRequest = nodeRequests.get(i);
            PlanNode node = keptNodes.get(nodeRequest.placeId());
            if (node == null) {
                node = day.addNode(places.get(nodeRequest.placeId()), i);
            } else {
                node.changeOrderIndex(i);
            }
            applyAlternatives(node, nodeRequest.alternativePlaceIdsOrEmpty(), places);
        }
    }

    /** 플랜B를 요청과 같게 맞춰요. 남는 플랜B는 그대로 둬요 */
    private void applyAlternatives(PlanNode node, List<Long> alternativePlaceIds, Map<Long, Place> places) {
        Set<Long> requested = Set.copyOf(alternativePlaceIds);
        Set<Long> kept = new HashSet<>();
        for (PlanAlternative alternative : new ArrayList<>(node.getAlternatives())) {
            Long placeId = alternative.getPlace().getPlaceId();
            if (requested.contains(placeId)) {
                kept.add(placeId);
            } else {
                node.removeAlternative(alternative);
            }
        }
        for (Long placeId : alternativePlaceIds) {
            if (!kept.contains(placeId)) {
                node.addAlternative(places.get(placeId));
            }
        }
    }

    /** 모든 일자가 완성이면 COMPLETED, 하나라도 아니면 DRAFT (decisions #11) */
    private PlanStatus refreshStatus(Plan plan) {
        boolean isAllCompleted = !plan.getDays().isEmpty()
                && plan.getDays().stream().allMatch(PlanDay::isCompleted);
        PlanStatus status = isAllCompleted ? PlanStatus.COMPLETED : PlanStatus.DRAFT;
        plan.changeStatus(status);
        return status;
    }

    private BigDecimal toCoordinate(BigDecimal value) {
        return value == null ? null : value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
}
