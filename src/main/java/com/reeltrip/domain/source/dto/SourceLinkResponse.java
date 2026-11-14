package com.reeltrip.domain.source.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.infra.ai.PlaceDraft;

public record SourceLinkResponse(Long sourceLinkId, String url, AnalysisStatus status, List<PlaceDraft> places,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String code,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String message) { }
