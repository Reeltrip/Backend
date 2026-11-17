package com.reeltrip.domain.user.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.user.dto.UserMeResponse;
import com.reeltrip.domain.user.dto.UserProfileUpdateRequest;
import com.reeltrip.domain.user.service.UserService;
import com.reeltrip.global.security.LoginUser;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

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

    /** 마이페이지 프로필 수정 (#79). 이메일은 바꾸지 않아요. */
    @PatchMapping("/me")
    public ResponseEntity<UserMeResponse> modifyMe(@LoginUser Long userId,
                                                   // 생략과 null을 구분하려고 JsonNode로 받지만, 문서에는 요청 형식으로 보여줘요.
                                                   @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                                                           schema = @Schema(implementation = UserProfileUpdateRequest.class)))
                                                   @RequestBody JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.modifyMe(userId, body));
    }
}
