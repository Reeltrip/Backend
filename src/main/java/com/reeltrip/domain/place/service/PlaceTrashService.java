package com.reeltrip.domain.place.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.dto.PlaceTrashResponse;
import com.reeltrip.domain.place.repository.PlaceTrashRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/**
 * 요약카드 휴지통 (#58, Figma 3-2·3-3). 카드를 휴지통에 넣는 건 PlaceService.removePlace예요.
 * 여러 장을 고르는 화면이라, 내 휴지통에 없는 카드가 하나라도 섞이면 404로 끝내고 아무것도 바꾸지 않아요.
 */
@Service
@Transactional(readOnly = true)
public class PlaceTrashService {

    private final PlaceTrashRepository placeTrashRepository;

    public PlaceTrashService(PlaceTrashRepository placeTrashRepository) {
        this.placeTrashRepository = placeTrashRepository;
    }

    /** GET /places/trash */
    public PlaceTrashResponse getTrash(Long userId) {
        List<PlaceTrashResponse.Item> items = placeTrashRepository.findTrashedByUserId(userId).stream()
                .map(PlaceTrashResponse.Item::from)
                .toList();
        return new PlaceTrashResponse(items, items.size());
    }

    /** POST /places/trash/restore. 보관함으로만 돌아와요(계획 자리는 복구 안 함) */
    @Transactional
    public void restore(Long userId, Collection<Long> placeIds) {
        Set<Long> ids = requireAllTrashed(userId, placeIds);
        placeTrashRepository.restore(userId, ids, LocalDateTime.now());
    }

    /** DELETE /places/trash?placeIds=. 되돌릴 수 없어요 */
    @Transactional
    public void purge(Long userId, Collection<Long> placeIds) {
        Set<Long> ids = requireAllTrashed(userId, placeIds);
        placeTrashRepository.deleteKeyPointsOfTrashed(userId, ids);
        placeTrashRepository.deleteTrashed(userId, ids);
    }

    private Set<Long> requireAllTrashed(Long userId, Collection<Long> placeIds) {
        if (placeIds == null || placeIds.isEmpty() || placeIds.contains(null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<Long> ids = new LinkedHashSet<>(placeIds);
        if (placeTrashRepository.countTrashed(userId, ids) != ids.size()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        return ids;
    }
}
