package com.reeltrip.domain.source.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.source.dto.SourceCreateRequest;
import com.reeltrip.domain.source.dto.SourceCreateResponse;
import com.reeltrip.domain.source.dto.SourceDetailResponse;
import com.reeltrip.domain.source.service.SourceService;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public ResponseEntity<SourceCreateResponse> createSource(@LoginUser Long userId,
                                                            @Valid @RequestBody SourceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(sourceService.createSource(userId, request));
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<SourceDetailResponse> getSource(@LoginUser Long userId, @PathVariable Long sourceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sourceService.getSource(userId, sourceId));
    }
}
