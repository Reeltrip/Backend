package com.reeltrip.domain.plan.entity;

import com.reeltrip.domain.place.entity.Place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 계획에서 고른 숙소 (ERD plan_stay). 계획 단위이고 STAY 카드만 넣어요. */
@Entity
@Table(name = "plan_stay", uniqueConstraints = @UniqueConstraint(
        name = "uk_plan_stay_plan_place", columnNames = {"plan_id", "place_id"}))
public class PlanStay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_stay_id")
    private Long planStayId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    protected PlanStay() {
    }

    PlanStay(Plan plan, Place place) {
        this.plan = plan;
        this.place = place;
    }

    public Long getPlanStayId() {
        return planStayId;
    }

    public Plan getPlan() {
        return plan;
    }

    public Place getPlace() {
        return place;
    }
}
