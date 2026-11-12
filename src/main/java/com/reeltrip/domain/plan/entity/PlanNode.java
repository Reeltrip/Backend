package com.reeltrip.domain.plan.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.reeltrip.domain.place.entity.Place;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 확정한 노드 (ERD plan_node). (plan_id, place_id) 유니크: 같은 장소 카드는 한 계획에 한 번만.
 * Hibernate는 한 트랜잭션에서 insert를 delete보다 먼저 보내요. 같은 장소를 지웠다 다시 넣으면 유니크에 걸리니
 * 남는 노드는 orderIndex만 고치거나, 지운 뒤 flush하고 넣어요.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "plan_node",
        uniqueConstraints = @UniqueConstraint(name = "uk_plan_node_plan_place", columnNames = {"plan_id", "place_id"}),
        indexes = @Index(name = "idx_plan_node_day_order", columnList = "plan_day_id, order_index"))
public class PlanNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_node_id")
    private Long planNodeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_day_id", nullable = false)
    private PlanDay planDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "planNode", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("planAlternativeId ASC")
    private List<PlanAlternative> alternatives = new ArrayList<>();

    protected PlanNode() {
    }

    PlanNode(Plan plan, PlanDay planDay, Place place, int orderIndex) {
        this.plan = plan;
        this.planDay = planDay;
        this.place = place;
        this.orderIndex = orderIndex;
    }

    public void changeOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public PlanAlternative addAlternative(Place place) {
        PlanAlternative alternative = new PlanAlternative(this, place);
        alternatives.add(alternative);
        return alternative;
    }

    public void removeAlternative(PlanAlternative alternative) {
        alternatives.remove(alternative);
    }

    public Long getPlanNodeId() {
        return planNodeId;
    }

    public Plan getPlan() {
        return plan;
    }

    public PlanDay getPlanDay() {
        return planDay;
    }

    public Place getPlace() {
        return place;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PlanAlternative> getAlternatives() {
        return alternatives;
    }
}
