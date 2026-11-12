package com.reeltrip.domain.plan.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 일자별 둘러볼 구역 (ERD plan_day_area). PlanDay.addArea / clearAreas로만 바뀌어요. */
@Entity
@Table(name = "plan_day_area")
public class PlanDayArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_day_area_id")
    private Long planDayAreaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_day_id", nullable = false)
    private PlanDay planDay;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    protected PlanDayArea() {
    }

    PlanDayArea(PlanDay planDay, String name, int orderIndex, BigDecimal latitude, BigDecimal longitude) {
        this.planDay = planDay;
        this.name = name;
        this.orderIndex = orderIndex;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getPlanDayAreaId() {
        return planDayAreaId;
    }

    public PlanDay getPlanDay() {
        return planDay;
    }

    public String getName() {
        return name;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
