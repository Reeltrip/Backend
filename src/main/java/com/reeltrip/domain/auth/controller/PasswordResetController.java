package com.reeltrip.domain.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.reeltrip.domain.auth.dto.*;
import com.reeltrip.domain.auth.service.PasswordResetDispatcher;
import com.reeltrip.domain.auth.service.PasswordResetService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetDispatcher dispatcher;
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetDispatcher dispatcher, PasswordResetService service) {
        this.dispatcher = dispatcher; this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        dispatcher.request(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").build();
    }

    @PostMapping("/verify")
    public ResponseEntity<PasswordResetVerifyResponse> verify(@RequestBody PasswordResetVerifyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .body(service.verify(request.token()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody PasswordResetConfirmRequest request) {
        service.confirm(request.token(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").build();
    }
}
