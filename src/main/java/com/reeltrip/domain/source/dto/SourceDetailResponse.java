package com.reeltrip.domain.source.dto;

import java.util.List;

import com.reeltrip.domain.source.entity.AnalysisStatus;

public record SourceDetailResponse(Long sourceId, String title, AnalysisStatus status,
                                   List<SourceLinkResponse> sourceLinks) { }
