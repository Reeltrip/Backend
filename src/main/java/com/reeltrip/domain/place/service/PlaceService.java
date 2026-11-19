package com.reeltrip.domain.place.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.domain.place.dto.PlaceCardRequest;
import com.reeltrip.domain.place.dto.PlaceCreateRequest;
import com.reeltrip.domain.place.dto.PlaceCreateResponse;
import com.reeltrip.domain.place.dto.PlaceListRequest;
import com.reeltrip.domain.place.dto.PlaceListResponse;
import com.reeltrip.domain.place.dto.PlaceResponse;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.place.repository.PlaceRepository;
import com.reeltrip.domain.plan.entity.PlanAlternative;
import com.reeltrip.domain.plan.entity.PlanNode;
import com.reeltrip.domain.plan.entity.PlanStay;
import com.reeltrip.domain.plan.repository.PlanAlternativeRepository;
import com.reeltrip.domain.plan.repository.PlanNodeRepository;
import com.reeltrip.domain.plan.repository.PlanStayRepository;
import com.reeltrip.domain.source.entity.SourceLink;
import com.reeltrip.domain.source.repository.SourceLinkRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** 요약카드 (S2-1, S3-1, S3-2, S3-3). 소유: place 팀원 */
@Service
@Transactional(readOnly = true)
public class PlaceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlaceRepository placeRepository;
    private final SourceLinkRepository sourceLinkRepository;
    private final UserRepository userRepository;
    private final PlanNodeRepository planNodeRepository;
    private final PlanAlternativeRepository planAlternativeRepository;
    private final PlanStayRepository planStayRepository;
    private final PlaceValidator placeValidator;
    private final ObjectMapper objectMapper;
    private final PlaceImageArchiver imageArchiver;

    public PlaceService(PlaceRepository placeRepository, SourceLinkRepository sourceLinkRepository,
                        UserRepository userRepository, PlanNodeRepository planNodeRepository,
                        PlanAlternativeRepository planAlternativeRepository, PlanStayRepository planStayRepository,
                        PlaceValidator placeValidator, ObjectMapper objectMapper,
                        PlaceImageArchiver imageArchiver) {
        this.placeRepository = placeRepository;
        this.sourceLinkRepository = sourceLinkRepository;
        this.userRepository = userRepository;
        this.planNodeRepository = planNodeRepository;
        this.planAlternativeRepository = planAlternativeRepository;
        this.planStayRepository = planStayRepository;
        this.placeValidator = placeValidator;
        this.objectMapper = objectMapper;
        this.imageArchiver = imageArchiver;
    }

    /**
     * POST /places. 형식이 틀린 카드가 한 장이라도 있으면 한 장도 저장하지 않아요(모두 검사한 뒤 저장).
     * AI 카드(sourceLinkId 있음) 중 Places 보강을 못 해 필수 항목이 빈 카드는 빼고 나머지만 저장하고 skipped로 알려요.
     * 직접 추가한 카드(S3-3)는 사용자가 채운 값이라 필수 항목이 비면 지금처럼 400이에요.
     */
    @Transactional
    public PlaceCreateResponse createPlaces(Long userId, PlaceCreateRequest request) {
        User user = userRepository.getReferenceById(userId);
        List<PendingCard> pending = new ArrayList<>();
        List<PlaceCreateResponse.Skipped> skipped = new ArrayList<>();
        for (int index = 0; index < request.places().size(); index++) {
            PlaceCardRequest card = request.places().get(index);
            if (card == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            SourceLink sourceLink = getOwnedSourceLink(userId, card.sourceLinkId());
            PlaceContent content = toContent(card);
            if (sourceLink == null) {
                placeValidator.validate(content, false);
                pending.add(new PendingCard(null, content));
                continue;
            }
            placeValidator.validateFormat(content, true);
            List<String> missingFields = placeValidator.missingRequiredFields(content);
            if (!missingFields.isEmpty()) {
                skipped.add(new PlaceCreateResponse.Skipped(
                        index, sourceLink.getSourceLinkId(), content.name(), missingFields));
                continue;
            }
            pending.add(new PendingCard(sourceLink, content));
        }
        // 모든 카드가 검사를 통과한 뒤에만 사진을 옮겨요. 같은 링크의 카드는 사진이 같아서 한 번만 옮겨요 (#87)
        Map<String, String> archivedUrls = new HashMap<>();
        List<Place> places = pending.stream().map(card -> {
            String imageUrl = card.content().imageUrl();
            String archived = imageUrl == null ? null : archivedUrls.computeIfAbsent(imageUrl, imageArchiver::archive);
            return Place.create(user, card.sourceLink(), card.content().withImageUrl(archived));
        }).toList();
        List<PlaceCreateResponse.Item> saved = placeRepository.saveAll(places).stream()
                .map(PlaceCreateResponse.Item::from)
                .toList();
        return new PlaceCreateResponse(saved, List.copyOf(skipped));
    }

    /** 검사를 통과해 저장을 기다리는 카드 */
    private record PendingCard(SourceLink sourceLink, PlaceContent content) { }

    /**
     * GET /places. categoryCounts는 category 필터와 관계없이 세고 반경 필터는 적용해요(decisions #11).
     * 반경 필터는 좌표가 있는 카드만 대상이에요.
     */
    public PlaceListResponse getPlaces(Long userId, PlaceListRequest request) {
        validateListRequest(request);
        List<Place> places = placeRepository.findAllByUser_UserIdOrderByCreatedAtDescPlaceIdDesc(userId);
        if (request.hasRadiusFilter()) {
            places = places.stream()
                    .filter(place -> isWithinRadius(place, request.latitude(), request.longitude(), request.radiusM()))
                    .toList();
        }

        Map<Category, Long> categoryCounts = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            categoryCounts.put(category, 0L);
        }
        places.forEach(place -> categoryCounts.merge(place.getCategory(), 1L, Long::sum));

        if (request.category() != null) {
            places = places.stream().filter(place -> place.getCategory() == request.category()).toList();
        }
        long from = (long) request.page() * request.size();
        List<PlaceListResponse.Item> pageItems = places.stream()
                .skip(from)
                .limit(request.size())
                .map(PlaceListResponse.Item::from)
                .toList();
        return new PlaceListResponse(pageItems, categoryCounts, request.page(), request.size(), places.size());
    }

    /** GET /places/{placeId} */
    public PlaceResponse getPlace(Long userId, Long placeId) {
        return PlaceResponse.from(getOwnedPlace(userId, placeId));
    }

    /**
     * PATCH /places/{placeId}. JSON에 들어온 필드만 바꾸고 null로 온 필드는 비워요.
     * 합친 결과로 저장 때와 같은 검사를 해요. sourceLinkId는 바꾸지 않아요(들어와도 무시).
     * 계획(노드·플랜B·숙소)에 쓰인 카드는 카테고리를 바꿀 수 없어요. 같은 카테고리로 보내는 건 통과.
     */
    @Transactional
    public PlaceResponse modifyPlace(Long userId, Long placeId, JsonNode body) {
        Place place = getOwnedPlace(userId, placeId);
        if (body == null || !body.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        PlaceCardRequest patch;
        try {
            patch = objectMapper.treeToValue(body, PlaceCardRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e);
        }
        PlaceContent merged = merge(place.toContent(), patch, body);
        placeValidator.validate(merged, place.getSourceLink() != null);
        // 계획에 쓰인 카드의 카테고리가 바뀌면 "교통·팁은 노드 불가", "숙소는 STAY만" 규칙이 깨질 수 있어서 막아요
        if (merged.category() != place.getCategory() && isUsedInPlan(placeId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        place.update(merged);
        placeRepository.flush(); // updatedAt을 응답에 바로 담으려고 여기서 반영해요
        return PlaceResponse.from(place);
    }

    /**
     * DELETE /places/{placeId}. 카드는 휴지통으로 옮기고(#58), 이 카드를 쓰는 플랜B·노드·숙소는 지워요(decisions #9).
     * 복구해도 보관함으로만 돌아오고 계획 자리는 돌아오지 않아요(사용자 결정 A안).
     * Hibernate는 orphan 삭제를 flush 때 큐에 넣어 카드 삭제보다 늦게 보낼 수 있어서 단계마다 flush해요.
     */
    @Transactional
    public void removePlace(Long userId, Long placeId) {
        Place place = getOwnedPlace(userId, placeId);

        for (PlanAlternative alternative : planAlternativeRepository.findAllByPlace_PlaceId(placeId)) {
            alternative.getPlanNode().removeAlternative(alternative);
        }
        placeRepository.flush();

        for (PlanNode node : planNodeRepository.findAllByPlace_PlaceId(placeId)) {
            node.getPlanDay().removeNode(node);
        }
        for (PlanStay stay : planStayRepository.findAllByPlace_PlaceId(placeId)) {
            stay.getPlan().removeStay(stay);
        }
        placeRepository.flush();

        place.moveToTrash(LocalDateTime.now());
    }

    /** GET /users/me의 보관함 카드 수 (BE 1이 씀). 교통·팁 포함 */
    public long countPlaces(Long userId) {
        return placeRepository.countByUser_UserId(userId);
    }

    /** 노드·플랜B·숙소 중 하나라도 이 카드를 쓰는지 */
    private boolean isUsedInPlan(Long placeId) {
        return !planNodeRepository.findAllByPlace_PlaceId(placeId).isEmpty()
                || !planAlternativeRepository.findAllByPlace_PlaceId(placeId).isEmpty()
                || !planStayRepository.findAllByPlace_PlaceId(placeId).isEmpty();
    }

    /** 남의 카드도 없는 카드와 같이 404로 (있다는 것도 알려주지 않음) */
    private Place getOwnedPlace(Long userId, Long placeId) {
        return placeRepository.findByPlaceIdAndUser_UserId(placeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
    }

    /** AI 카드의 원본 링크는 부른 사람이 분석한 링크여야 해요 */
    private SourceLink getOwnedSourceLink(Long userId, Long sourceLinkId) {
        if (sourceLinkId == null) {
            return null;
        }
        SourceLink sourceLink = sourceLinkRepository.findById(sourceLinkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        if (!sourceLink.getSource().getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return sourceLink;
    }

    private void validateListRequest(PlaceListRequest request) {
        boolean hasOnlyOneCoordinate = (request.latitude() == null) != (request.longitude() == null);
        if (hasOnlyOneCoordinate
                || request.radiusM() <= 0
                || request.page() < 0
                || request.size() < 1 || request.size() > MAX_PAGE_SIZE
                || request.sortType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.hasRadiusFilter() && !PlaceValidator.isValidCoordinate(request.latitude(), request.longitude())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isWithinRadius(Place place, double latitude, double longitude, int radiusM) {
        if (place.getLatitude() == null || place.getLongitude() == null) {
            return false;
        }
        return Haversine.distanceM(latitude, longitude,
                place.getLatitude().doubleValue(), place.getLongitude().doubleValue()) <= radiusM;
    }

    private PlaceContent toContent(PlaceCardRequest card) {
        return new PlaceContent(card.category(), card.name(), card.address(), toDecimal(card.latitude()),
                toDecimal(card.longitude()), card.googlePlaceId(), card.city(), card.imageUrl(), card.sourceUrl(),
                card.sourceTimestampSec(), card.keyPoints(), card.priceText(), card.reviewCount(),
                card.openingHours(), card.accessText(), card.memo(), card.subType(), card.applicableRegion(),
                card.cost(), card.benefit(), card.method(), card.placeText(), card.caution());
    }

    private PlaceContent merge(PlaceContent old, PlaceCardRequest patch, JsonNode body) {
        return new PlaceContent(
                pick(body, "category", patch.category(), old.category()),
                pick(body, "name", patch.name(), old.name()),
                pick(body, "address", patch.address(), old.address()),
                pick(body, "latitude", toDecimal(patch.latitude()), old.latitude()),
                pick(body, "longitude", toDecimal(patch.longitude()), old.longitude()),
                pick(body, "googlePlaceId", patch.googlePlaceId(), old.googlePlaceId()),
                pick(body, "city", patch.city(), old.city()),
                pick(body, "imageUrl", patch.imageUrl(), old.imageUrl()),
                pick(body, "sourceUrl", patch.sourceUrl(), old.sourceUrl()),
                pick(body, "sourceTimestampSec", patch.sourceTimestampSec(), old.sourceTimestampSec()),
                pick(body, "keyPoints", patch.keyPoints(), old.keyPoints()),
                pick(body, "priceText", patch.priceText(), old.priceText()),
                pick(body, "reviewCount", patch.reviewCount(), old.reviewCount()),
                pick(body, "openingHours", patch.openingHours(), old.openingHours()),
                pick(body, "accessText", patch.accessText(), old.accessText()),
                pick(body, "memo", patch.memo(), old.memo()),
                pick(body, "subType", patch.subType(), old.subType()),
                pick(body, "applicableRegion", patch.applicableRegion(), old.applicableRegion()),
                pick(body, "cost", patch.cost(), old.cost()),
                pick(body, "benefit", patch.benefit(), old.benefit()),
                pick(body, "method", patch.method(), old.method()),
                pick(body, "placeText", patch.placeText(), old.placeText()),
                pick(body, "caution", patch.caution(), old.caution()));
    }

    /** JSON에 필드가 있으면(null 포함) 새 값, 없으면 기존 값 */
    private static <T> T pick(JsonNode body, String field, T newValue, T oldValue) {
        return body.has(field) ? newValue : oldValue;
    }

    private static BigDecimal toDecimal(Double value) {
        return PlaceValidator.toCoordinate(value);
    }
}
