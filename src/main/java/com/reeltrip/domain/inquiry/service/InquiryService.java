package com.reeltrip.domain.inquiry.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reeltrip.domain.inquiry.dto.InquiryCreateRequest;
import com.reeltrip.domain.inquiry.dto.InquiryCreateResponse;
import com.reeltrip.domain.inquiry.entity.Inquiry;
import com.reeltrip.domain.inquiry.repository.InquiryRepository;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

@Service
public class InquiryService {

    private final InquiryRepository inquiries;
    private final UserRepository users;
    private final Clock clock;

    public InquiryService(InquiryRepository inquiries, UserRepository users, Clock clock) {
        this.inquiries = inquiries;
        this.users = users;
        this.clock = clock;
    }

    /** 마이페이지 1:1 문의 접수 (#79). 답변 알림은 희망 여부만 저장하고 보내지 않아요. */
    @Transactional
    public InquiryCreateResponse createInquiry(Long userId, InquiryCreateRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Inquiry inquiry = inquiries.save(Inquiry.create(user, request.type(), request.content().strip(),
                request.wantsReplyNotification(), now));
        return new InquiryCreateResponse(inquiry.getInquiryId(),
                inquiry.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime());
    }
}
