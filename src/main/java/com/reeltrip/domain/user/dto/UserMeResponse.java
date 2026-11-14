package com.reeltrip.domain.user.dto;

public record UserMeResponse(Long userId, String email, String nickname, long placeCount, long planCount) { }
