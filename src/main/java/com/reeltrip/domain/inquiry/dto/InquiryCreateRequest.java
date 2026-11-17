package com.reeltrip.domain.inquiry.dto;

import com.reeltrip.domain.inquiry.entity.InquiryType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 작성자는 토큰으로 정해요. 본문에 회원 ID를 받지 않아요. 내용 300자는 FE 입력 제한과 같아요. */
public record InquiryCreateRequest(@NotNull InquiryType type,
                                   @NotBlank @Size(max = 300) String content,
                                   @NotNull Boolean wantsReplyNotification) { }
