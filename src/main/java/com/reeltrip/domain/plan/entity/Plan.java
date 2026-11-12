package com.reeltrip.domain.plan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.reeltrip.domain.place.entity.Place;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 여행계획 (ERD plan). user는 만든 사람(OWNER)이고, plan_member에도 OWNER 행을 같이 둬요.
 * 계획을 지우면 일자·구역·노드·플랜B·숙소·멤버가 같이 지워져요(cascade).
 */
@Entity
@Table(name = "plan")
public class Plan extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "city_place_id", length = 255)
    private String cityPlaceId;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status;

    /** null이면 공유 꺼짐 */
    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", length = 20)
    private ShareType shareType;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<PlanDay> days = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("planStayId ASC")
    private List<PlanStay> stays = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("planMemberId ASC")
    private List<PlanMember> members = new ArrayList<>();

    protected Plan() {
    }

    private Plan(User user, String name, LocalDate startDate, LocalDate endDate, String city,
                 String cityPlaceId, BigDecimal latitude, BigDecimal longitude) {
        this.user = user;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.city = city;
        this.cityPlaceId = cityPlaceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = PlanStatus.DRAFT;
    }

    /** DRAFT로 만들어요. 일자·OWNER 멤버는 서비스가 addDay, addMember로 붙여요. */
    public static Plan create(User owner, String name, LocalDate startDate, LocalDate endDate, String city,
                              String cityPlaceId, BigDecimal latitude, BigDecimal longitude) {
        return new Plan(owner, name, startDate, endDate, city, cityPlaceId, latitude, longitude);
    }

    public void modifyInfo(String name, LocalDate startDate, LocalDate endDate, String city,
                           String cityPlaceId, BigDecimal latitude, BigDecimal longitude) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.city = city;
        this.cityPlaceId = cityPlaceId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void changeStatus(PlanStatus status) {
        this.status = status;
    }

    /** null을 넘기면 공유를 꺼요 */
    public void changeShareType(ShareType shareType) {
        this.shareType = shareType;
    }

    public PlanDay addDay(int dayNumber, LocalDate date) {
        PlanDay day = new PlanDay(this, dayNumber, date);
        days.add(day);
        return day;
    }

    /** 일자를 빼면 그 일자의 구역·노드·플랜B도 같이 지워져요(orphanRemoval) */
    public void removeDay(PlanDay day) {
        days.remove(day);
    }

    public PlanStay addStay(Place place) {
        PlanStay stay = new PlanStay(this, place);
        stays.add(stay);
        return stay;
    }

    public void removeStay(PlanStay stay) {
        stays.remove(stay);
    }

    public PlanMember addMember(User user, PlanRole role) {
        PlanMember member = new PlanMember(this, user, role);
        members.add(member);
        return member;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getUserId().equals(userId);
    }

    public boolean isShared() {
        return shareType != null;
    }

    public Long getPlanId() {
        return planId;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getCity() {
        return city;
    }

    public String getCityPlaceId() {
        return cityPlaceId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public ShareType getShareType() {
        return shareType;
    }

    public List<PlanDay> getDays() {
        return days;
    }

    public List<PlanStay> getStays() {
        return stays;
    }

    public List<PlanMember> getMembers() {
        return members;
    }
}
