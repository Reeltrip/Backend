package com.reeltrip.domain.plan;

import java.math.BigDecimal;
import java.util.List;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.Place;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.domain.plan.entity.Plan;
import com.reeltrip.domain.plan.entity.PlanRole;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.support.TestFixtures;

/** plan-edit 테스트용 카드·계획 만들기 */
public final class PlanEditFixtures {

    private PlanEditFixtures() {
    }

    /** 장소 카드(FOOD·CAFE·STAY 등) 또는 교통·팁 카드 */
    public static Place place(User owner, Category category, String name) {
        PlaceContent content = category.isPlace()
                ? new PlaceContent(category, name, "福岡市博多区", new BigDecimal("33.5889000"),
                        new BigDecimal("130.4245000"), null, "후쿠오카", "https://storage.googleapis.com/test.jpg",
                        null, null, List.of(), null, null, null, null, null,
                        null, null, null, null, null, null, null)
                : new PlaceContent(category, name, null, null, null, null, "후쿠오카", null,
                        null, null, List.of(), null, null, null, null, null,
                        "교통패스", "후쿠오카 시내", "640엔", null, null, null, null);
        return Place.create(owner, null, content);
    }

    /** 11/14부터 dayCount일짜리 계획. OWNER 멤버와 일자를 같이 붙여요 */
    public static Plan plan(User owner, int dayCount) {
        Plan plan = Plan.create(owner, "후쿠오카 여행", TestFixtures.date(11, 14),
                TestFixtures.date(11, 14).plusDays(dayCount - 1L), "후쿠오카", null, null, null);
        plan.addMember(owner, PlanRole.OWNER);
        for (int i = 0; i < dayCount; i++) {
            plan.addDay(i + 1, TestFixtures.date(11, 14).plusDays(i));
        }
        return plan;
    }
}
