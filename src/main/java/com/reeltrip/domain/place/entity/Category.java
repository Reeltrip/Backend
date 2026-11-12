package com.reeltrip.domain.place.entity;

/**
 * 요약카드 카테고리 (와이어프레임 S2-0). 키 이름은 FE categories.ts와 같아야 해요. 바꾸면 FE에 알려요.
 * 장소 6종은 지도 노드가 되고, 교통·팁은 노드·숙소·플랜B에 넣을 수 없어요.
 */
public enum Category {
    FOOD("맛집", "대표메뉴", true),
    CAFE("카페", "대표메뉴", true),
    STAY("숙소", "특징", true),
    SIGHTSEEING("관광지", "볼거리", true),
    ACTIVITY("액티비티", "즐길거리", true),
    SHOPPING("쇼핑", "대표상품", true),
    TRANSPORT("교통", null, false),
    TIP("팁", null, false);

    /** 카드당 핵심 포인트 최대 개수 */
    public static final int MAX_KEY_POINTS = 3;

    private final String displayName;
    private final String keyPointName;
    private final boolean isPlace;

    Category(String displayName, String keyPointName, boolean isPlace) {
        this.displayName = displayName;
        this.keyPointName = keyPointName;
        this.isPlace = isPlace;
    }

    /** 화면 이름. 응답에는 .name()을 내려주고 화면 이름은 FE가 붙여요. 프롬프트·로그용이에요. */
    public String getDisplayName() {
        return displayName;
    }

    /** 핵심 포인트 이름(대표메뉴, 특징 등). 교통·팁은 null */
    public String getKeyPointName() {
        return keyPointName;
    }

    /** 지도 노드가 되는 장소 카드인지 */
    public boolean isPlace() {
        return isPlace;
    }
}
