package com.reeltrip.domain.source;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.service.PlaceEnrichmentService;
import com.reeltrip.infra.ai.ExtractionResult;
import com.reeltrip.infra.ai.PlaceDraft;
import com.reeltrip.infra.fetcher.FetchedContent;
import com.reeltrip.infra.map.PlacesClient;

/** 링크 분석으로 만든 장소 카드가 주소·좌표 없이 저장에서 막히지 않게 기본값을 확인해요. */
@SpringBootTest
class PlaceEnrichmentDefaultTest {

    @Autowired PlaceEnrichmentService enrichment;
    @MockitoBean PlacesClient places;

    @Test
    @DisplayName("따로 설정하지 않으면 링크 분석의 장소 카드를 Google Places로 보강한다.")
    void enrichmentOnByDefault() throws Exception {
        // given
        given(places.search(anyString())).willReturn(List.of());
        PlaceDraft draft = new PlaceDraft(null, Category.FOOD, "하카타 잇소", null, null, null, null, null, null, null, null,
                List.of(), null, null, null, null, null, null, null, null, null, null, null, null);
        // when
        enrichment.enrich(1L, new FetchedContent(Platform.YOUTUBE, "https://youtu.be/abcDEF12345", null, null),
                new ExtractionResult("후쿠오카", List.of(draft)));
        // then
        verify(places).search("하카타 잇소 후쿠오카");
    }
}
