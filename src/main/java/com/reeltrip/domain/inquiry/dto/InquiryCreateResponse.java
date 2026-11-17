package com.reeltrip.domain.inquiry.dto;

import java.time.OffsetDateTime;

public record InquiryCreateResponse(Long inquiryId, OffsetDateTime createdAt) { }
