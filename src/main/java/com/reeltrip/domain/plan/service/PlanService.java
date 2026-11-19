package com.reeltrip.domain.plan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.dto.PlanCopyResponse;
import com.reeltrip.domain.plan.dto.PlanCreateRequest;
import com.reeltrip.domain.plan.dto.PlanCreateResponse;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.dto.PlanListRequest;
import com.reeltrip.domain.plan.dto.PlanListResponse;
import com.reeltrip.domain.plan.dto.PlanSummaryResponse;
import com.reeltrip.domain.plan.dto.PlanUpdateRequest;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanAlternative;
import com.reeltrip.domain.plan.entity.PlanDay;
import com.reeltrip.domain.plan.entity.PlanDayArea;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.plan.entity.PlanStatus;
import com.reeltrip.domain.plan.entity.PlanStay;
import com.reeltrip.domain.plan.repository.PlanMemberRepository;
import com.reeltrip.domain.plan.repository.PlanRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 계획 만들기·목록·보기·수정·삭제·복제 (S4-0, S4-1, S4-2, S4-7, S5-1). 소유: plan 팀원 */
@Service
public class PlanService {

    /** 지도 노드가 되는 장소 6종. 계획을 만들 때 불러오는 카드 */
    private static final List<Category> PLACE_CATEGORIES =
            Arrays.stream(Category.values()).filter(Category::isPlace).toList();
    /** 계획 지도 "팁" 탭에 보여 주는 카드 */
    private static final List<Category> TIP_CATEGORIES = List.of(Category.TRANSPORT, Category.TIP);

    private final PlanRepository planRepository;
    private final PlanMemberRepository planMemberRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final PlanAccessService planAccessService;
    private final Clock clock;

    public PlanService(PlanRepository planRepository, PlanMemberRepository planMemberRepository,
                       PlaceRepository placeRepository, UserRepository userRepository,
                       PlanAccessService planAccessService, Clock clock) {
        this.planRepository = planRepository;
        this.planMemberRepository = planMemberRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.planAccessService = planAccessService;
        this.clock = clock;
    }

    /** GET /users/me의 여행목록 수: 내 계획 + 공유받은 계획 (BE 1이 씀) */
    @Transactional(readOnly = true)
    public long countPlans(Long userId) {
        return planMemberRepository.countByUser_UserId(userId);
    }

    /** S4-0(status=DRAFT), S5-1(owner=me|shared, period=UPCOMING|PAST). 생성 최신순 */
    @Transactional(readOnly = true)
    public PlanListResponse getPlans(Long userId, PlanListRequest request) {
        List<PlanRole> roles = switch (request.owner() == null ? "" : request.owner()) {
            case "me" -> List.of(PlanRole.OWNER);
            case "shared" -> List.of(PlanRole.EDITOR, PlanRole.VIEWER);
            default -> List.of(PlanRole.values());
        };
        LocalDate today = LocalDate.now(clock);
        LocalDate endDateFrom = "UPCOMING".equals(request.period()) ? today : null;
        LocalDate endDateBefore = "PAST".equals(request.period()) ? today : null;
        PageRequest pageable = PageRequest.of(request.page(), request.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("planId")));

        Page<Plan> plans = planRepository.findAllByMember(userId, roles, request.status(),
                endDateFrom, endDateBefore, pageable);
        return new PlanListResponse(plans.map(PlanSummaryResponse::from).getContent(), plans.getTotalElements());
    }

    /** S4-1·S4-2. 기간으로 1일차~n일차를 만들고, 그 도시의 장소 카드 수를 알려 줘요(0개여도 만듦). */
    @Transactional
    public PlanCreateResponse createPlan(Long userId, PlanCreateRequest request) {
        validatePeriod(request.startDate(), request.endDate());
        User owner = findUser(userId);

        Plan plan = Plan.create(owner, request.name(), request.startDate(), request.endDate(), request.city(),
                request.cityPlaceId(), toBigDecimal(request.latitude()), toBigDecimal(request.longitude()));
        plan.addMember(owner, PlanRole.OWNER);
        int dayCount = countDays(request.startDate(), request.endDate());
        for (int dayNumber = 1; dayNumber <= dayCount; dayNumber++) {
            plan.addDay(dayNumber, request.startDate().plusDays(dayNumber - 1));
        }
        planRepository.save(plan);

        long loadedPlaceCount = placeRepository.findAllInSameCity(userId, plan.getCity(), PLACE_CATEGORIES).size();
        return new PlanCreateResponse(plan.getPlanId(), plan.getStatus(), dayCount, loadedPlaceCount);
    }

    /** S4-7. 공유가 켜진 계획을 처음 연 로그인 사용자는 PlanAccessService가 멤버로 넣어요. */
    @Transactional
    public PlanDetailResponse getPlan(Long planId, Long userId) {
        PlanAccess access = planAccessService.getReadablePlan(planId, userId);
        return toDetail(access.plan(), access.role());
    }

    /**
     * Figma 계획 편집 (docs/decisions.md #6). 기간이 줄면 없어지는 일자를 노드·플랜B·구역과 같이 지우고,
     * 늘면 새 일자를 붙여요. 남은 일자 날짜는 시작일 기준으로 다시 매겨요.
     */
    @Transactional
    public PlanDetailResponse modifyPlan(Long planId, Long userId, PlanUpdateRequest request) {
        PlanAccess access = planAccessService.getEditablePlan(planId, userId);
        Plan plan = access.plan();

        LocalDate startDate = request.startDate() != null ? request.startDate() : plan.getStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : plan.getEndDate();
        validatePeriod(startDate, endDate);

        String name = request.name() != null ? request.name() : plan.getName();
        if (request.city() != null) {
            plan.modifyInfo(name, startDate, endDate, request.city(), request.cityPlaceId(),
                    toBigDecimal(request.latitude()), toBigDecimal(request.longitude()));
        } else {
            plan.modifyInfo(name, startDate, endDate, plan.getCity(), plan.getCityPlaceId(),
                    plan.getLatitude(), plan.getLongitude());
        }
        resizeDays(plan, startDate, endDate);
        refreshStatus(plan);

        // 새로 붙인 일자의 planDayId를 응답에 담으려고 먼저 저장해요.
        planRepository.flush();
        return toDetail(plan, access.role());
    }

    /** S5-1. OWNER만. 일자·노드·플랜B·숙소·멤버가 같이 지워져요(cascade). */
    @Transactional
    public void removePlan(Long planId, Long userId) {
        Plan plan = planAccessService.getOwnedPlan(planId, userId).plan();
        planRepository.delete(plan);
    }

    /**
     * S5-1. 볼 수 있는 계획을 부른 사람의 DRAFT 계획으로 복제해요 (docs/decisions.md #8).
     * 카드는 같은 카드를 가리키고, 완성 여부는 모두 미완성, 공유는 꺼짐, 멤버는 부른 사람(OWNER)만.
     */
    @Transactional
    public PlanCopyResponse copyPlan(Long planId, Long userId) {
        Plan source = planAccessService.getReadablePlan(planId, userId).plan();
        User caller = findUser(userId);

        Plan copy = Plan.create(caller, source.getName(), source.getStartDate(), source.getEndDate(),
                source.getCity(), source.getCityPlaceId(), source.getLatitude(), source.getLongitude());
        copy.addMember(caller, PlanRole.OWNER);
        source.getDays().stream()
                .sorted(Comparator.comparingInt(PlanDay::getDayNumber))
                .forEach(day -> copyDay(day, copy.addDay(day.getDayNumber(), day.getDate())));
        for (PlanStay stay : source.getStays()) {
            copy.addStay(stay.getPlace());
        }
        planRepository.save(copy);
        return new PlanCopyResponse(copy.getPlanId());
    }

    private void copyDay(PlanDay from, PlanDay to) {
        from.getAreas().stream()
                .sorted(Comparator.comparingInt(PlanDayArea::getOrderIndex))
                .forEach(area -> to.addArea(area.getName(), area.getLatitude(), area.getLongitude()));
        from.getNodes().stream()
                .sorted(Comparator.comparingInt(PlanNode::getOrderIndex))
                .forEach(node -> {
                    PlanNode copied = to.addNode(node.getPlace(), node.getOrderIndex());
                    for (PlanAlternative alternative : node.getAlternatives()) {
                        copied.addAlternative(alternative.getPlace());
                    }
                });
    }

    /** 기간에 맞게 일자를 지우거나 붙이고, 날짜를 startDate + dayNumber - 1로 다시 매겨요. */
    private void resizeDays(Plan plan, LocalDate startDate, LocalDate endDate) {
        int dayCount = countDays(startDate, endDate);
        for (PlanDay day : new ArrayList<>(plan.getDays())) {
            if (day.getDayNumber() > dayCount) {
                plan.removeDay(day);
            }
        }
        Set<Integer> dayNumbers = plan.getDays().stream()
                .map(PlanDay::getDayNumber)
                .collect(Collectors.toSet());
        for (int dayNumber = 1; dayNumber <= dayCount; dayNumber++) {
            if (!dayNumbers.contains(dayNumber)) {
                plan.addDay(dayNumber, startDate.plusDays(dayNumber - 1));
            }
        }
        for (PlanDay day : plan.getDays()) {
            day.changeDate(startDate.plusDays(day.getDayNumber() - 1));
        }
    }

    /** 모든 일자가 완성이면 COMPLETED, 하나라도 미완성이면 DRAFT (docs/decisions.md #11) */
    private void refreshStatus(Plan plan) {
        boolean isAllCompleted = plan.getDays().stream().allMatch(PlanDay::isCompleted);
        plan.changeStatus(isAllCompleted ? PlanStatus.COMPLETED : PlanStatus.DRAFT);
    }

    private PlanDetailResponse toDetail(Plan plan, PlanRole role) {
        List<Place> tips = placeRepository.findAllInSameCity(
                plan.getUser().getUserId(), plan.getCity(), TIP_CATEGORIES);
        return PlanDetailResponse.of(plan, role, tips);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_PLAN_PERIOD);
        }
    }

    private int countDays(LocalDate startDate, LocalDate endDate) {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /** 좌표 컬럼이 DECIMAL(10,7)이라 소수 7자리로 맞춰요. */
    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }
}
