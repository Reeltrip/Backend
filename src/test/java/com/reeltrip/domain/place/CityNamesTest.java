package com.reeltrip.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.reeltrip.domain.place.service.CityNames;

/** 계획 도시(도시 검색 결과)와 카드 도시(AI가 뽑은 이름)를 같은 도시로 맞춰 봐요 (#97) */
class CityNamesTest {

    @Test
    @DisplayName("끝의 행정 단위(시·부·도·현·특별시·광역시)와 띄어쓰기가 달라도 같은 도시다.")
    void sameCity_ignoresAdministrativeSuffix() {
        assertThat(CityNames.isSame("오사카시", "오사카")).isTrue();
        assertThat(CityNames.isSame("오사카부", "오사카시")).isTrue();
        assertThat(CityNames.isSame("후쿠오카 현", "후쿠오카")).isTrue();
        assertThat(CityNames.isSame("서울특별시", "서울")).isTrue();
        assertThat(CityNames.isSame("부산광역시", "부산")).isTrue();
        assertThat(CityNames.isSame("제주도", "제주")).isTrue();
        assertThat(CityNames.isSame("Osaka City", "osaka")).isTrue();
    }

    @Test
    @DisplayName("다른 도시이거나 비어 있으면 같은 도시가 아니다.")
    void differentOrEmpty_notSame() {
        assertThat(CityNames.isSame("오사카시", "교토시")).isFalse();
        assertThat(CityNames.isSame("오사카", null)).isFalse();
        assertThat(CityNames.isSame(null, null)).isFalse();
        assertThat(CityNames.isSame(" ", "")).isFalse();
    }

    @Test
    @DisplayName("끝말을 떼면 한 글자만 남는 이름은 떼지 않는다.")
    void shortName_keepsSuffix() {
        // "부산시"는 "부산", 하지만 "도시"는 "도"만 남아서 떼지 않아요
        assertThat(CityNames.isSame("부산시", "부산")).isTrue();
        assertThat(CityNames.isSame("도시", "도")).isFalse();
    }
}
