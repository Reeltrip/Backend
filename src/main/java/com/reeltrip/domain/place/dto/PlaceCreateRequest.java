package com.reeltrip.domain.place.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** POST /places (S2-1 모두 저장, S3-3 직접 추가) */
public record PlaceCreateRequest(
        @NotEmpty List<PlaceCardRequest> places
) {
}
