package com.reeltrip.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.user.entity.User;

/** 테스트에서 같이 쓰는 데이터 만들기. 팀원은 필요하면 자기 테스트 폴더에 따로 만들어 써요. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static User user(String email) {
        return User.create(email, "{noop}password1", "여행자", false, LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    public static PlaceContent foodContent(String name, String city) {
        return new PlaceContent(Category.FOOD, name, "福岡市博多区博多駅東3-1-6",
                new BigDecimal("33.5889000"), new BigDecimal("130.4245000"), "ChIJ-test", city,
                "https://storage.googleapis.com/test.jpg", null, null, List.of("돈코츠 라멘"), "¥960~980",
                6500, "11:00-24:00", "하카타역 도보 6분", null,
                null, null, null, null, null, null, null);
    }

    public static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
