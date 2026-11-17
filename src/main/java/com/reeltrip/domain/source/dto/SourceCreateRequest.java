package com.reeltrip.domain.source.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** S1-2 링크 입력. 시연 흐름(링크 7개)을 한 번에 받도록 최대 10개예요. */
public record SourceCreateRequest(@NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 2048) String> urls) {
    @Override
    public String toString() {
        return "SourceCreateRequest[urls=REDACTED]";
    }
}
