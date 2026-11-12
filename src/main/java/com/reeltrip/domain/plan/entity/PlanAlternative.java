package com.reeltrip.domain.plan.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.reeltrip.domain.place.entity.Place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 플랜B (ERD plan_alternative). 대신할 노드가 지워지면 같이 지워져요(PlanNode.alternatives orphanRemoval). */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "plan_alternative")
public class PlanAlternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_alternative_id")
    private Long planAlternativeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_node_id", nullable = false)
    private PlanNode planNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PlanAlternative() {
    }

    PlanAlternative(PlanNode planNode, Place place) {
        this.planNode = planNode;
        this.place = place;
    }

    public Long getPlanAlternativeId() {
        return planAlternativeId;
    }

    public PlanNode getPlanNode() {
        return planNode;
    }

    public Place getPlace() {
        return place;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
