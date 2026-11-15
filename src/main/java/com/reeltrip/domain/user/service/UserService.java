package com.reeltrip.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.place.service.PlaceService;
import com.reeltrip.domain.plan.service.PlanService;
import com.reeltrip.domain.user.dto.UserMeResponse;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

@Service
public class UserService {

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
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new UserMeResponse(user.getUserId(), user.getEmail(), user.getNickname(),
                places.countPlaces(userId), plans.countPlans(userId));
    }
}
