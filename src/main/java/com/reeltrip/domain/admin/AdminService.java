package com.reeltrip.domain.admin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.dto.PlaceCardRequest;
import com.reeltrip.domain.place.dto.PlaceCreateRequest;
import com.reeltrip.domain.place.dto.PlaceResponse;
import com.reeltrip.domain.place.service.PlaceService;
import com.reeltrip.domain.place.service.PlaceTrashService;
import com.reeltrip.domain.plan.dto.PlanCreateRequest;
import com.reeltrip.domain.plan.dto.PlanDayUpdateRequest;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.domain.plan.service.PlanEditService;
import com.reeltrip.domain.plan.service.PlanService;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

@Service
@Transactional(readOnly = true)
public class AdminService {
    private final AdminAccess access;
    private final AdminDataRepository data;
    private final UserRepository users;
    private final PlaceService places;
    private final PlaceTrashService trash;
    private final PlanService plans;
    private final PlanEditService planEdit;
    private final AdminAudit audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AdminService(AdminAccess access, AdminDataRepository data, UserRepository users, PlaceService places,
                        PlaceTrashService trash, PlanService plans, PlanEditService planEdit, AdminAudit audit,
                        ObjectMapper mapper, Clock clock) {
        this.access = access; this.data = data; this.users = users; this.places = places; this.trash = trash;
        this.plans = plans; this.planEdit = planEdit; this.audit = audit; this.mapper = mapper; this.clock = clock;
    }

    public Account me(Long actor) { access.requireAdmin(actor); return account(findUser(actor)); }

    public Map<String, Object> users(Long actor, String query, int page, int size) {
        access.requireAdmin(actor);
        var found = data.users(query.strip(), page, size);
        return page("users", new AdminDataRepository.Page<>(found.items().stream()
                .map(row -> new Account(row.userId(), row.email(), row.nickname(), access.isDemo(row.userId()))).toList(),
                page, size, found.hasNext()));
    }

    public Map<String, Object> sources(Long actor, Long userId, int page, int size) {
        target(actor, userId, false);
        return page("links", data.sources(userId, page, size));
    }

    public Map<String, Object> places(Long actor, Long userId, boolean deleted, int page, int size) {
        target(actor, userId, false);
        return page("places", data.places(userId, deleted, page, size));
    }

    public PlaceResponse place(Long actor, Long userId, Long placeId) {
        target(actor, userId, false);
        return places.getPlace(userId, placeId);
    }

    @Transactional
    public PlaceResponse editPlace(Long actor, Long userId, Long placeId, JsonNode body) {
        target(actor, userId, true);
        var result = places.modifyPlace(userId, placeId, body);
        audit.changed(actor, "PLACE_UPDATE", userId, placeId);
        return result;
    }

    @Transactional
    public void trashPlace(Long actor, Long userId, Long placeId) {
        target(actor, userId, true);
        places.removePlace(userId, placeId);
        audit.changed(actor, "PLACE_TRASH", userId, placeId);
    }

    @Transactional
    public void restorePlace(Long actor, Long userId, Long placeId) {
        target(actor, userId, true);
        trash.restore(userId, List.of(placeId));
        audit.changed(actor, "PLACE_RESTORE", userId, placeId);
    }

    public Map<String, Object> plans(Long actor, Long userId, int page, int size) {
        target(actor, userId, false);
        return page("plans", data.plans(userId, page, size));
    }

    @Transactional
    public PlanDetailResponse plan(Long actor, Long userId, Long planId) {
        target(actor, userId, false);
        data.requireOwnedPlan(userId, planId);
        return plans.getPlan(planId, userId);
    }

    @Transactional
    public void removePlan(Long actor, Long userId, Long planId) {
        target(actor, userId, true);
        plans.removePlan(planId, userId);
        audit.changed(actor, "PLAN_DELETE", userId, planId);
    }

    public ResetPreview preview(Long actor, Long userId) {
        target(actor, userId, false);
        access.requireDemo(userId);
        var snapshot = data.snapshot(userId, false);
        var blockers = data.blockers(userId);
        return new ResetPreview(userId, counts(snapshot), blockers.isEmpty(), blockers,
                fingerprint(userId, snapshot), "초기화 " + userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public Map<String, Integer> reset(Long actor, Long userId, String fingerprint, String confirmation) {
        access.requireAdmin(actor);
        access.requireDemo(userId);
        target(actor, userId, true);
        var snapshot = data.snapshot(userId, true);
        if (!data.blockers(userId).isEmpty()) { throw new BusinessException(ErrorCode.ADMIN_OPERATION_BLOCKED); }
        if (!("초기화 " + userId).equals(confirmation) || !fingerprint(userId, snapshot).equals(fingerprint)) {
            throw new BusinessException(ErrorCode.ADMIN_PREVIEW_CHANGED);
        }
        // 지정 계정의 확정된 ID만 FK 역순으로 삭제해요. 실패하면 모든 삭제를 롤백해요.
        data.deleteSnapshot(snapshot);
        audit.changed(actor, "DEMO_RESET", userId, userId);
        return counts(snapshot);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public Map<String, Object> seed(Long actor, Long userId, String imageUrl) {
        access.requireAdmin(actor);
        access.requireDemo(userId);
        target(actor, userId, true);
        var snapshot = data.snapshot(userId, true);
        if (snapshot.values().stream().anyMatch(ids -> !ids.isEmpty())) {
            throw new BusinessException(ErrorCode.ADMIN_DEMO_NOT_EMPTY);
        }
        validateImageUrl(imageUrl);
        // Google 데이터를 복사하지 않는 직접 작성 예시예요. 장소명·주소·좌표는 시연용이에요.
        var cards = List.of(sample("FOOD", "[데모] 점심 식당", "서울 종로구 시연용 식당", 37.5720, 126.9860, imageUrl),
                sample("SIGHTSEEING", "[데모] 산책 장소", "서울 종로구 시연용 산책 장소", 37.5750, 126.9890, imageUrl));
        var created = places.createPlaces(userId, new PlaceCreateRequest(cards));
        var tomorrow = LocalDate.now(clock).plusDays(1);
        var plan = plans.createPlan(userId, new PlanCreateRequest("[데모] 서울 하루 여행", tomorrow, tomorrow,
                "서울", null, 37.5720, 126.9860));
        planEdit.saveDay(plan.planId(), 1, userId, new PlanDayUpdateRequest(created.places().stream()
                .map(card -> new PlanDayUpdateRequest.Node(card.placeId(), List.of())).toList(), false));
        audit.changed(actor, "DEMO_SEED", userId, plan.planId());
        return Map.of("planId", plan.planId(), "places", created.places());
    }

    private PlaceCardRequest sample(String category, String name, String address, double lat, double lng, String image) {
        return mapper.convertValue(Map.of("category", category, "name", name, "address", address,
                "latitude", lat, "longitude", lng, "city", "서울", "imageUrl", image,
                "memo", "시연용 예시이며 실제 장소 정보가 아니에요."), PlaceCardRequest.class);
    }

    private static void validateImageUrl(String value) {
        try {
            var uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && List.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.INVALID_INPUT); }
    }

    private User target(Long actor, Long id, boolean lock) {
        access.requireAdmin(actor);
        return lock ? users.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)) : findUser(id);
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Account account(User user) {
        return new Account(user.getUserId(), user.getEmail(), user.getNickname(), access.isDemo(user.getUserId()));
    }

    private static Map<String, Object> page(String name, AdminDataRepository.Page<?> page) {
        return Map.of(name, page.items(), "page", page.page(), "size", page.size(), "hasNext", page.hasNext());
    }

    private static Map<String, Integer> counts(Map<String, List<Long>> snapshot) {
        var counts = new LinkedHashMap<String, Integer>();
        snapshot.forEach((table, ids) -> counts.put(table, ids.size()));
        return counts;
    }

    private static String fingerprint(Long userId, Map<String, List<Long>> snapshot) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + snapshot).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record Account(Long userId, String email, String nickname, boolean demo) { }
    public record ResetPreview(Long userId, Map<String, Integer> counts, boolean allowed, List<String> blockers,
                               String fingerprint, String confirmation) { }
}
