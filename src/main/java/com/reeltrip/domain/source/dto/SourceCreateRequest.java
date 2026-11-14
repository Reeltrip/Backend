package com.reeltrip.domain.source.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SourceCreateRequest(@NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 2048) String> urls) {
    @Override
    public String toString() {
        return "SourceCreateRequest[urls=REDACTED]";
    }
}
