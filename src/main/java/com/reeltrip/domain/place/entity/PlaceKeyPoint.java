package com.reeltrip.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 요약카드 핵심 포인트 (ERD place_key_point). 카드당 최대 3개, Place.update로만 바뀌어요. */
@Entity
@Table(name = "place_key_point")
public class PlaceKeyPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_key_point_id")
    private Long placeKeyPointId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected PlaceKeyPoint() {
    }

    PlaceKeyPoint(Place place, String content, int orderIndex) {
        this.place = place;
        this.content = content;
        this.orderIndex = orderIndex;
    }

    public Long getPlaceKeyPointId() {
        return placeKeyPointId;
    }

    public Place getPlace() {
        return place;
    }

    public String getContent() {
        return content;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}
