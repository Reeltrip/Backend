package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.service.PlaceEnrichmentService;
import com.reeltrip.infra.ai.ExtractionResult;
import com.reeltrip.infra.ai.PlaceDraft;
import com.reeltrip.infra.fetcher.FetchedContent;
import com.reeltrip.infra.map.MapsPlace;
import com.reeltrip.infra.map.PlacesClient;

class PlaceEnrichmentTest {
    private final PlacesClient places=mock(PlacesClient.class);
    private final FetchedContent content=new FetchedContent(Platform.YOUTUBE,"https://youtu.be/abcDEF12345",null,"https://i.ytimg.com/vi/abcDEF12345/hqdefault.jpg");

    @Test @DisplayName("기본 비활성 상태는 Google을 부르지 않고 출처·시점·도시·null을 유지한다.")
    void disabledKeepsDraftAndTrustedSource() {
        // given / when
        var result=new PlaceEnrichmentService(places,false).enrich(7L,content,new ExtractionResult("교토",List.of(draft(Category.FOOD))));
        // then
        var card=result.getFirst();
        assertThat(card.sourceLinkId()).isEqualTo(7L); assertThat(card.sourceUrl()).isEqualTo(content.url());
        assertThat(card.sourceTimestampSec()).isEqualTo(42); assertThat(card.city()).isEqualTo("교토");
        assertThat(card.address()).isNull(); assertThat(card.imageUrl()).isEqualTo(content.thumbnailUrl());
        verifyNoInteractions(places);
    }

    @Test @DisplayName("허용된 보강은 장소만 검색하고 교통·팁과 기존 카드 내용을 바꾸지 않는다.")
    void enrichesOnlyPlaces() throws Exception {
        // given
        when(places.search(anyString())).thenReturn(List.of(new MapsPlace("id","가게","교토 주소",35.0,135.0,100,List.of("매일 10시"),null,List.of())));
        // when
        var cards=new PlaceEnrichmentService(places,true).enrich(7L,content,new ExtractionResult("교토",List.of(draft(Category.FOOD),draft(Category.TIP))));
        // then
        assertThat(cards.getFirst().address()).isEqualTo("교토 주소"); assertThat(cards.getFirst().name()).isEqualTo("가게");
        assertThat(cards.getFirst().priceText()).isEqualTo("¥500"); assertThat(cards.get(1).address()).isNull();
        verify(places,times(1)).search("가게 교토");
    }

    @Test @DisplayName("장소를 못 찾거나 검색이 실패해도 초안은 남긴다.")
    void searchFailureDoesNotLoseCards() throws Exception {
        // given
        when(places.search(anyString())).thenThrow(new java.io.IOException());
        // when / then
        assertThat(new PlaceEnrichmentService(places,true).enrich(7L,content,new ExtractionResult(null,List.of(draft(Category.FOOD)))))
                .hasSize(1).first().extracting(PlaceDraft::address).isNull();
    }

    @Test @DisplayName("Google 영업시간·주소가 길어도 카드 저장 한도(영업시간 100자·주소 255자)에 맞춰 넣는다.")
    void enrichedValuesFitCardLimits() throws Exception {
        // given: 요일별 영업시간 7줄(합치면 100자를 훌쩍 넘음)과 아주 긴 주소
        var week=List.of("월요일: 오전 11:00~오후 10:00","화요일: 오전 11:00~오후 10:00","수요일: 오전 11:00~오후 10:00",
                "목요일: 오전 11:00~오후 10:00","금요일: 오전 11:00~오후 11:00","토요일: 오전 10:00~오후 11:00","일요일: 오전 10:00~오후 9:00");
        when(places.search(anyString())).thenReturn(List.of(new MapsPlace("id","가게","가".repeat(300),35.0,135.0,10,week,null,List.of())));
        // when
        var card=new PlaceEnrichmentService(places,true).enrich(7L,content,new ExtractionResult("교토",List.of(draft(Category.FOOD)))).getFirst();
        // then: 요일 중간에서 끊지 않고, 줄였다는 표시를 붙임
        assertThat(card.openingHours()).hasSizeLessThanOrEqualTo(100).startsWith("월요일: 오전 11:00~오후 10:00").endsWith("…");
        assertThat(card.openingHours().substring(0,card.openingHours().length()-1)).endsWith("10:00");
        assertThat(card.address()).hasSizeLessThanOrEqualTo(255);
    }

    private static PlaceDraft draft(Category category) {
        return new PlaceDraft(null,category,"가게",null,null,null,null,null,null,"untrusted",42,
                List.of("여행"),"¥500",null,null,null,null,null,null,null,null,null,null,null);
    }
}
