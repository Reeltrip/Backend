package com.reeltrip.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ExtractionResponseParserPriceTest {

    private final ExtractionResponseParser parser = new ExtractionResponseParser(new ObjectMapper());

    private PlaceDraft parseOne(String category, String priceText) {
        String json = """
                { "city": "오사카", "cards": [
                  { "category": "%s", "name": "테스트 장소", "priceText": %s }
                ] }
                """.formatted(category, priceText == null ? "null" : "\"" + priceText + "\"");
        return parser.parse(json, "https://youtu.be/test", true).cards().get(0);
    }

    @Test
    @DisplayName("숙소 카드는 영상에 요금이 나와도 가격을 비운다.")
    void stay_priceAlwaysNull() {
        // given: 엔화 요금이 나온 숙소 카드
        // when
        PlaceDraft draft = parseOne("STAY", "¥12,000부터");
        // then
        assertThat(draft.priceText()).isNull();
    }

    @Test
    @DisplayName("원화로 바꾼 가격은 현지통화 기준이 아니라서 비운다.")
    void wonPrice_dropped() {
        // given / when / then
        assertThat(parseOne("FOOD", "10,000원~18,000원").priceText()).isNull();
        assertThat(parseOne("ACTIVITY", "₩25,000").priceText()).isNull();
    }

    @Test
    @DisplayName("현지 통화로 적힌 가격은 그대로 넣는다.")
    void localCurrencyPrice_kept() {
        // given / when / then
        assertThat(parseOne("FOOD", "¥960~980").priceText()).isEqualTo("¥960~980");
        assertThat(parseOne("SIGHTSEEING", "1,078엔부터").priceText()).isEqualTo("1,078엔부터");
    }
}
