package com.reeltrip.domain.user.service;

import java.net.URI;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.place.service.PlaceService;
import com.reeltrip.domain.plan.service.PlanService;
import com.reeltrip.domain.user.dto.UserMeResponse;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

@Service
public class UserService {

    // 가입(SignupRequest)과 같은 닉네임 길이예요.
    private static final int MAX_NICKNAME_LENGTH = 8;
    private static final int MAX_AVATAR_URL_LENGTH = 2048;

    private final UserRepository users;
    private final PlaceService places;
    private final PlanService plans;

    public UserService(UserRepository users, PlaceService places, PlanService plans) {
        this.users = users;
        this.places = places;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return toResponse(findUser(userId));
    }

    /** 마이페이지 프로필 수정 (#79). 보낸 필드만 바꾸고, avatarUrl이 null이면 기본 사진으로 돌려요. */
    @Transactional
    public UserMeResponse modifyMe(Long userId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        User user = findUser(userId);
        String nickname = body.has("nickname") ? validNickname(body.get("nickname")) : user.getNickname();
        String avatarUrl = body.has("avatarUrl") ? validAvatarUrl(body.get("avatarUrl")) : user.getAvatarUrl();
        user.changeProfile(nickname, avatarUrl);
        return toResponse(user);
    }

    private User findUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private UserMeResponse toResponse(User user) {
        return UserMeResponse.of(user, places.countPlaces(user.getUserId()), plans.countPlans(user.getUserId()));
    }

    private static String validNickname(JsonNode value) {
        String nickname = value.isTextual() ? value.asText().strip() : "";
        if (nickname.isEmpty() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return nickname;
    }

    /** POST /images가 돌려준 https 주소만 받아요. 다른 스킴은 화면의 img에 넣기 위험해서 막아요. */
    private static String validAvatarUrl(JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        String url = value.isTextual() ? value.asText() : "";
        try {
            URI uri = URI.create(url);
            if (url.length() <= MAX_AVATAR_URL_LENGTH && "https".equals(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null) {
                return url;
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 입력 오류로 응답해요.
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
