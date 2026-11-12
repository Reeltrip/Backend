package com.reeltrip.domain.plan.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.reeltrip.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 계획 멤버 (ERD plan_member). 만든 사람도 OWNER 행으로 들어가요. */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "plan_member", uniqueConstraints = @UniqueConstraint(
        name = "uk_plan_member_plan_user", columnNames = {"plan_id", "user_id"}))
public class PlanMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_member_id")
    private Long planMemberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private PlanRole role;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PlanMember() {
    }

    PlanMember(Plan plan, User user, PlanRole role) {
        this.plan = plan;
        this.user = user;
        this.role = role;
    }

    public void changeRole(PlanRole role) {
        this.role = role;
    }

    public Long getPlanMemberId() {
        return planMemberId;
    }

    public Plan getPlan() {
        return plan;
    }

    public User getUser() {
        return user;
    }

    public PlanRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
