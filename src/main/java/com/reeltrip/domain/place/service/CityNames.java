package com.reeltrip.domain.place.service;

import java.util.List;
import java.util.Locale;

/**
 * 도시 이름 비교. 계획 도시는 FE 도시 검색 결과("오사카시"), 링크 분석 카드의 도시는 AI가 뽑은 이름("오사카")이라
 * 글자가 달라요. 끝의 행정 단위와 띄어쓰기·대소문자를 떼고 비교해요 (#97)
 */
public final class CityNames {

    /** 긴 것부터 확인해요("특별시"를 "시"보다 먼저) */
    private static final List<String> SUFFIXES = List.of(
            "특별자치시", "특별자치도", "특별시", "광역시", "city", "시", "부", "도", "현");
    /** 끝말을 떼고 남아야 하는 최소 글자 수. "도시"처럼 한 글자만 남으면 떼지 않아요 */
    private static final int MIN_NAME_LENGTH = 2;

    private CityNames() {
    }

    public static boolean isSame(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isEmpty() && a.equals(b);
    }

    static String normalize(String city) {
        if (city == null) {
            return "";
        }
        String name = city.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (String suffix : SUFFIXES) {
            if (name.endsWith(suffix) && name.length() - suffix.length() >= MIN_NAME_LENGTH) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }
}
