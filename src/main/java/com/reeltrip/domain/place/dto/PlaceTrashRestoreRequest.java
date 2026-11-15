package com.reeltrip.domain.place.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** POST /places/trash/restore. 휴지통에서 고른 카드들 (Figma 3-3 선택 → 복구) */
public record PlaceTrashRestoreRequest(@NotEmpty List<@NotNull Long> placeIds) {
}
