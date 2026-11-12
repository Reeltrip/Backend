package com.reeltrip.domain.place.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.reeltrip.domain.source.entity.SourceLink;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.global.common.BaseTimeEntity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** 요약카드 (ERD place). 장소 6종과 교통·팁을 한 테이블에 담아요. 교통·팁 전용 컬럼은 장소 카드에서 null. */
@Entity
@Table(name = "place", indexes = {
        @Index(name = "idx_place_user_category", columnList = "user_id, category"),
        @Index(name = "idx_place_user_city", columnList = "user_id, city")
})
public class Place extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long placeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** AI가 만든 카드만 있음. 직접 추가한 카드는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_link_id")
    private SourceLink sourceLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_timestamp_sec")
    private Integer sourceTimestampSec;

    @Column(name = "price_text", length = 50)
    private String priceText;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "opening_hours", length = 100)
    private String openingHours;

    @Column(name = "access_text", length = 255)
    private String accessText;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "sub_type", length = 30)
    private String subType;

    @Column(name = "applicable_region", length = 100)
    private String applicableRegion;

    @Column(name = "cost", length = 255)
    private String cost;

    @Column(name = "benefit", columnDefinition = "TEXT")
    private String benefit;

    @Column(name = "method", columnDefinition = "TEXT")
    private String method;

    @Column(name = "place_text", length = 255)
    private String placeText;

    @Column(name = "caution", columnDefinition = "TEXT")
    private String caution;

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<PlaceKeyPoint> keyPoints = new ArrayList<>();

    protected Place() {
    }

    private Place(User user, SourceLink sourceLink) {
        this.user = user;
        this.sourceLink = sourceLink;
    }

    /** sourceLink는 직접 추가한 카드면 null */
    public static Place create(User user, SourceLink sourceLink, PlaceContent content) {
        Place place = new Place(user, sourceLink);
        place.update(content);
        return place;
    }

    /** 값을 통째로 바꿔요. PATCH는 서비스에서 기존 값과 합친 PlaceContent를 넘겨요. 핵심 포인트도 다시 매겨요. */
    public void update(PlaceContent content) {
        this.category = content.category();
        this.name = content.name();
        this.address = content.address();
        this.latitude = content.latitude();
        this.longitude = content.longitude();
        this.googlePlaceId = content.googlePlaceId();
        this.city = content.city();
        this.imageUrl = content.imageUrl();
        this.sourceUrl = content.sourceUrl();
        this.sourceTimestampSec = content.sourceTimestampSec();
        this.priceText = content.priceText();
        this.reviewCount = content.reviewCount();
        this.openingHours = content.openingHours();
        this.accessText = content.accessText();
        this.memo = content.memo();
        this.subType = content.subType();
        this.applicableRegion = content.applicableRegion();
        this.cost = content.cost();
        this.benefit = content.benefit();
        this.method = content.method();
        this.placeText = content.placeText();
        this.caution = content.caution();
        replaceKeyPoints(content.keyPoints());
    }

    private void replaceKeyPoints(List<String> contents) {
        this.keyPoints.clear();
        if (contents == null) {
            return;
        }
        for (int i = 0; i < contents.size(); i++) {
            this.keyPoints.add(new PlaceKeyPoint(this, contents.get(i), i));
        }
    }

    /** 지금 값을 PlaceContent로 꺼내요. PATCH에서 바뀐 필드만 덮어쓸 때 써요. */
    public PlaceContent toContent() {
        return new PlaceContent(category, name, address, latitude, longitude, googlePlaceId, city, imageUrl,
                sourceUrl, sourceTimestampSec, getKeyPointContents(), priceText, reviewCount, openingHours,
                accessText, memo, subType, applicableRegion, cost, benefit, method, placeText, caution);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    public List<String> getKeyPointContents() {
        return keyPoints.stream().map(PlaceKeyPoint::getContent).toList();
    }

    public Long getPlaceId() {
        return placeId;
    }

    public User getUser() {
        return user;
    }

    public SourceLink getSourceLink() {
        return sourceLink;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public String getCity() {
        return city;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Integer getSourceTimestampSec() {
        return sourceTimestampSec;
    }

    public String getPriceText() {
        return priceText;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public String getAccessText() {
        return accessText;
    }

    public String getMemo() {
        return memo;
    }

    public String getSubType() {
        return subType;
    }

    public String getApplicableRegion() {
        return applicableRegion;
    }

    public String getCost() {
        return cost;
    }

    public String getBenefit() {
        return benefit;
    }

    public String getMethod() {
        return method;
    }

    public String getPlaceText() {
        return placeText;
    }

    public String getCaution() {
        return caution;
    }

    public List<PlaceKeyPoint> getKeyPoints() {
        return keyPoints;
    }
}
