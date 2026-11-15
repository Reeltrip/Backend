package com.reeltrip.domain.source.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.source.dto.SourceListResponse;
import com.reeltrip.domain.source.service.SourceHistoryService;
import com.reeltrip.global.security.LoginUser;

/** S1 링크 홈. 접수·상세 조회와 별도로 목록만 읽어요. */
@RestController
public class SourceHistoryController {

    private final SourceHistoryService sourceHistory;

    public SourceHistoryController(SourceHistoryService sourceHistory) {
        this.sourceHistory = sourceHistory;
    }

    @GetMapping("/sources")
    public ResponseEntity<SourceListResponse> getSources(@LoginUser Long userId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sourceHistory.getSources(userId, page, size));
    }
}
