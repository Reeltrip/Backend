package com.reeltrip.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** PATCH /users/me 문서용 모양이에요. 보낸 필드만 바꾸려고 컨트롤러는 JsonNode로 받아요. */
public record UserProfileUpdateRequest(
        @Schema(description = "1~8자. 생략하면 그대로예요.", example = "보영") String nickname,
        @Schema(nullable = true, description = "POST /images로 받은 https 주소, 2048자 이하. 생략하면 그대로, null이면 기본 사진이에요.")
        String avatarUrl) { }
