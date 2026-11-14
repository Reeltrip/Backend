package com.reeltrip.domain.user.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.user.dto.UserMeResponse;
import com.reeltrip.domain.user.service.UserService;
import com.reeltrip.global.security.LoginUser;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getMe(@LoginUser Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.getMe(userId));
    }
}
