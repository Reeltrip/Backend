package com.reeltrip.domain.inquiry.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.reeltrip.domain.inquiry.dto.InquiryCreateRequest;
import com.reeltrip.domain.inquiry.dto.InquiryCreateResponse;
import com.reeltrip.domain.inquiry.service.InquiryService;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;

@RestController
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    /** 마이페이지 1:1 문의 접수 (#79) */
    @PostMapping("/inquiries")
    public ResponseEntity<InquiryCreateResponse> createInquiry(@LoginUser Long userId,
                                                               @Valid @RequestBody InquiryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(inquiryService.createInquiry(userId, request));
    }
}
