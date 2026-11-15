package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.service.PlaceEnrichmentService;
import com.reeltrip.infra.ai.FakeLlmClient;
import com.reeltrip.infra.fetcher.FetchedContent;
import com.reeltrip.infra.map.*;

@SpringBootTest
class PlaceEnrichmentDefaultTest {
    @Autowired PlaceEnrichmentService service;
    @MockitoBean PlacesClient places;

    @Test @DisplayName("명시적으로 켜지 않은 실제 앱은 검색이 가능해도 영구 보강하지 않고 출처와 빈 필드를 보존한다.")
    void applicationDefaultsToNoPersistentEnrichment() throws Exception {
        // given
        when(places.search(anyString())).thenReturn(List.of(new MapsPlace("id","가게","검색 주소",35.0,135.0,
                100,List.of("매일 10시"),null,List.of())));
        var content=new FetchedContent(Platform.YOUTUBE,"https://youtu.be/abcDEF12345",null,null);
        // when
        var card=service.enrich(7L,content,new FakeLlmClient().extractCards(content)).getFirst();
        // then
        assertThat(card.sourceLinkId()).isEqualTo(7L);
        assertThat(card.sourceUrl()).isEqualTo(content.url());
        assertThat(card.sourceTimestampSec()).isEqualTo(42);
        assertThat(card.address()).isNull();
        assertThat(card.latitude()).isNull();
        assertThat(card.openingHours()).isNull();
        verifyNoInteractions(places);
    }
}
