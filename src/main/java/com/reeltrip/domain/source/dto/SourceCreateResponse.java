package com.reeltrip.domain.source.dto;

import com.reeltrip.domain.source.entity.AnalysisStatus;

public record SourceCreateResponse(Long sourceId, AnalysisStatus status) { }
