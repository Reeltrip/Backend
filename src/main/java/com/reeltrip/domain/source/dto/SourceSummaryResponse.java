package com.reeltrip.domain.source.dto;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import com.reeltrip.domain.source.entity.AnalysisStatus;
import com.reeltrip.domain.source.entity.Source;
import com.reeltrip.global.config.TimeConfig;

public record SourceSummaryResponse(Long sourceId, String title, AnalysisStatus status, OffsetDateTime createdAt) {

    public static SourceSummaryResponse from(Source source) {
        return new SourceSummaryResponse(source.getSourceId(), source.getTitle(), source.getStatus(),
                source.getCreatedAt().atZone(TimeConfig.SEOUL).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS));
    }
}
