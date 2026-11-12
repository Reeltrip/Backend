package com.reeltrip.domain.plan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.reeltrip.domain.place.entity.Place;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 여행 일자 (ERD plan_day). (plan_id, day_number) 유니크라서 일자 순서를 바꿀 때는
 * 겹치지 않는 임시 번호로 먼저 바꾸고 flush한 뒤 최종 번호를 매겨야 해요.
 */
@Entity
@Table(name = "plan_day", uniqueConstraints = @UniqueConstraint(
        name = "uk_plan_day_plan_day_number", columnNames = {"plan_id", "day_number"}))
public class PlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_day_id")
    private Long planDayId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted;

    @OneToMany(mappedBy = "planDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<PlanDayArea> areas = new ArrayList<>();

    @OneToMany(mappedBy = "planDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<PlanNode> nodes = new ArrayList<>();

    protected PlanDay() {
    }

    PlanDay(Plan plan, int dayNumber, LocalDate date) {
        this.plan = plan;
        this.dayNumber = dayNumber;
        this.date = date;
        this.isCompleted = false;
    }

    public void changeDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }

    public void changeDate(LocalDate date) {
        this.date = date;
    }

    public void changeCompleted(boolean isCompleted) {
        this.isCompleted = isCompleted;
    }

    public PlanDayArea addArea(String name, BigDecimal latitude, BigDecimal longitude) {
        PlanDayArea area = new PlanDayArea(this, name, areas.size(), latitude, longitude);
        areas.add(area);
        return area;
    }

    public void clearAreas() {
        areas.clear();
    }

    public PlanNode addNode(Place place, int orderIndex) {
        PlanNode node = new PlanNode(plan, this, place, orderIndex);
        nodes.add(node);
        return node;
    }

    /** 노드를 빼면 그 노드의 플랜B도 같이 지워져요(orphanRemoval) */
    public void removeNode(PlanNode node) {
        nodes.remove(node);
    }

    public Long getPlanDayId() {
        return planDayId;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public List<PlanDayArea> getAreas() {
        return areas;
    }

    public List<PlanNode> getNodes() {
        return nodes;
    }
}
