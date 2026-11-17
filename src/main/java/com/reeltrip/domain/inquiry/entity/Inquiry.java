package com.reeltrip.domain.inquiry.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.reeltrip.domain.user.entity.User;

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

/** 마이페이지 1:1 문의 (#79). 답변 기능은 아직 없어서 접수 내용만 저장해요. */
@Entity
@Table(name = "inquiry")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private InquiryType type;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "wants_reply_notification", nullable = false)
    private boolean wantsReplyNotification;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Inquiry() { }

    public static Inquiry create(User user, InquiryType type, String content, boolean wantsReplyNotification,
                                 LocalDateTime createdAt) {
        Inquiry inquiry = new Inquiry();
        inquiry.user = Objects.requireNonNull(user);
        inquiry.type = Objects.requireNonNull(type);
        inquiry.content = Objects.requireNonNull(content);
        inquiry.wantsReplyNotification = wantsReplyNotification;
        inquiry.createdAt = Objects.requireNonNull(createdAt);
        return inquiry;
    }

    public Long getInquiryId() { return inquiryId; }
    public User getUser() { return user; }
    public InquiryType getType() { return type; }
    public String getContent() { return content; }
    public boolean isWantsReplyNotification() { return wantsReplyNotification; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
