package com.reeltrip.domain.place;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.PlaceContent;

/** place 팀원 테스트 전용 데이터 */
public final class PlaceTestFixtures {

    private PlaceTestFixtures() {
    }

    /** 장소 카드를 원하는 카테고리·좌표로 */
    public static PlaceContent placeAt(Category category, String name, double latitude, double longitude) {
        return new PlaceContent(category, name, "大阪市中央区", BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude), null, "오사카", "https://storage.googleapis.com/test.jpg", null, null,
                List.of(), null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static PlaceContent tip(String name) {
        return new PlaceContent(Category.TIP, name, null, null, null, null, "오사카", null, null, null,
                List.of(), null, null, null, null, null,
                "환전", "오사카", null, null, null, null, null);
    }

    /** POST /places 요청의 장소 카드 한 장 (직접 추가, 필수 항목 모두 채움) */
    public static Map<String, Object> foodCard(String name) {
        Map<String, Object> card = new HashMap<>();
        card.put("category", "FOOD");
        card.put("name", name);
        card.put("address", "福岡市博多区博多駅東3-1-6");
        card.put("latitude", 33.5889);
        card.put("longitude", 130.4245);
        card.put("imageUrl", "https://storage.googleapis.com/test.jpg");
        card.put("city", "후쿠오카");
        card.put("keyPoints", List.of("돈코츠 라멘"));
        card.put("priceText", "¥960~980");
        return card;
    }

    /** POST /places 요청의 교통 카드 한 장 */
    public static Map<String, Object> transportCard(String name) {
        Map<String, Object> card = new HashMap<>();
        card.put("category", "TRANSPORT");
        card.put("name", name);
        card.put("subType", "교통패스");
        card.put("applicableRegion", "오사카");
        card.put("cost", "¥3,300 (1일권)");
        return card;
    }
}
