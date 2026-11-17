package com.reeltrip.domain.user.dto;

import com.reeltrip.domain.user.entity.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserMeResponse(Long userId, String email, String nickname, long placeCount, long planCount,
                             @Schema(nullable = true, description = "프로필 사진 주소. 없으면 null이고 FE가 기본 사진을 보여줘요.")
                             String avatarUrl) {

    public static UserMeResponse of(User user, long placeCount, long planCount) {
        return new UserMeResponse(user.getUserId(), user.getEmail(), user.getNickname(), placeCount, planCount,
                user.getAvatarUrl());
    }
}
