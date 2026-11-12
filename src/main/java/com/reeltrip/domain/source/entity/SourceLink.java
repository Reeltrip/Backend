// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.source.entity;

import com.reeltrip.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 분석할 링크 하나와 그 상태·카드 초안 (ERD source_link). place.source_link_id가 이 테이블을 가리켜요. */
@Entity
@Table(name = "source_link")
public class SourceLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_link_id")
    private Long sourceLinkId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "fail_code", length = 50)
    private String failCode;

    @Column(name = "fail_message", length = 255)
    private String failMessage;

    @Column(name = "draft_json", columnDefinition = "TEXT")
    private String draftJson;

    protected SourceLink() {
    }

    private SourceLink(Source source, String url, Platform platform) {
        this.source = source;
        this.url = url;
        this.platform = platform;
        this.status = AnalysisStatus.PENDING;
    }

    public static SourceLink create(Source source, String url, Platform platform) {
        return new SourceLink(source, url, platform);
    }

    public Long getSourceLinkId() {
        return sourceLinkId;
    }

    public Source getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public Platform getPlatform() {
        return platform;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getFailCode() {
        return failCode;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public String getDraftJson() {
        return draftJson;
    }
}
