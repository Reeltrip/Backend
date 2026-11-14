// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.source.entity;

import com.reeltrip.domain.user.entity.User;
import com.reeltrip.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 링크 분석 요청 한 번 (ERD source). */
@Entity
@Table(name = "source", indexes = @Index(name = "idx_source_user_created", columnList = "user_id, created_at"))
public class Source extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "title", length = 50)
    private String title;

    protected Source() {
    }

    private Source(User user) {
        this.user = user;
        this.status = AnalysisStatus.PENDING;
    }

    public static Source create(User user) {
        return new Source(user);
    }

    public void markAnalyzing() {
        requireOpen();
        status = AnalysisStatus.ANALYZING;
    }

    public void markDone(String title) {
        if (status != AnalysisStatus.ANALYZING) {
            throw new IllegalStateException("분석 중인 요청만 완료할 수 있어요.");
        }
        assignTitle(title);
        status = AnalysisStatus.DONE;
    }

    public void markFailed(String title) {
        requireOpen();
        assignTitle(title);
        status = AnalysisStatus.FAILED;
    }

    private void requireOpen() {
        if (status == AnalysisStatus.DONE || status == AnalysisStatus.FAILED) {
            throw new IllegalStateException("종료한 분석 요청은 다시 변경할 수 없어요.");
        }
    }

    private void assignTitle(String title) {
        if (title == null || title.isBlank() || title.length() > 50) {
            throw new IllegalArgumentException("수집 제목은 1~50자여야 해요.");
        }
        this.title = title;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public User getUser() {
        return user;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
