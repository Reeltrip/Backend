package com.reeltrip.domain.source.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.infra.ai.ExtractionResult;
import com.reeltrip.infra.ai.PlaceDraft;
import com.reeltrip.infra.fetcher.FetchedContent;
import com.reeltrip.infra.map.MapsPlace;
import com.reeltrip.infra.map.PlacesClient;

@Service
public class PlaceEnrichmentService {
    /** place 테이블·PlaceValidator 한도. 넘기면 "모두 저장"이 INVALID_INPUT으로 막혀요. */
    static final int MAX_ADDRESS_LENGTH=255;
    static final int MAX_OPENING_HOURS_LENGTH=100;
    static final int MAX_PRICE_TEXT_LENGTH=50;
    private static final String HOURS_SEPARATOR="; ";
    private static final String CUT_MARK="…";

    private final PlacesClient places;
    private final boolean enabled;
    // AI는 주소·좌표를 만들지 않아서(LLM이 지어낸 주소 방지) 보강이 꺼져 있으면 장소 카드를 저장할 수 없어요. 그래서 기본은 켬.
    public PlaceEnrichmentService(PlacesClient places,@Value("${app.maps.persist-enrichment:true}") boolean enabled) {
        this.places=places; this.enabled=enabled;
    }

    public List<PlaceDraft> enrich(Long linkId,FetchedContent content,ExtractionResult result) {
        var drafts=new ArrayList<PlaceDraft>();
        int attempts=0;
        for(var draft:result.cards()) {
            MapsPlace place=null;
            if(enabled && draft.category().isPlace() && attempts<10 && !Thread.currentThread().isInterrupted()) {
                attempts++;
                String city=draft.city()==null?result.city():draft.city();
                String query=draft.name()+(city==null?"":" "+city);
                try { place=places.search(query.substring(0,Math.min(query.length(),200))).stream().findFirst().orElse(null); }
                catch(IOException | RuntimeException ignored) { /* 정보가 없으면 초안을 유지해요. */ }
            }
            drafts.add(new PlaceDraft(linkId,draft.category(),draft.name(),place==null?draft.address():cut(place.address(),MAX_ADDRESS_LENGTH),
                    place==null?draft.latitude():place.latitude(),place==null?draft.longitude():place.longitude(),
                    place==null?draft.googlePlaceId():place.googlePlaceId(),draft.city()==null?result.city():draft.city(),
                    draft.imageUrl()==null?content.thumbnailUrl():draft.imageUrl(),content.url(),draft.sourceTimestampSec(),
                    draft.keyPoints(),price(draft,place),place==null?draft.reviewCount():place.reviewCount(),
                    place==null?draft.openingHours():hours(place),draft.accessText(),draft.memo(),draft.subType(),
                    draft.applicableRegion(),draft.cost(),draft.benefit(),draft.method(),draft.placeText(),draft.caution()));
        }
        return List.copyOf(drafts);
    }

    /** 요일 문구를 이어 붙이되 100자를 넘으면 요일 단위로 끊고 "…"을 붙여요(요일 중간에서 자르지 않음). */
    private static String hours(MapsPlace place) {
        String value=String.join(HOURS_SEPARATOR,place.openingHours());
        if(value.isBlank()) { return null; }
        if(value.length()<=MAX_OPENING_HOURS_LENGTH) { return value; }
        var fitted=new StringBuilder();
        for(String day:place.openingHours()) {
            String next=fitted.isEmpty()?day:fitted+HOURS_SEPARATOR+day;
            if(next.length()+CUT_MARK.length()>MAX_OPENING_HOURS_LENGTH) { break; }
            fitted.setLength(0); fitted.append(next);
        }
        return fitted.isEmpty()?cut(value,MAX_OPENING_HOURS_LENGTH):fitted+CUT_MARK;
    }

    /** AI가 영상에서 뽑은 가격이 있으면 그 값을 쓰고, 없을 때만 Google 가격으로 채워요. 숙소는 날짜마다 달라 항상 비워요. */
    private static String price(PlaceDraft draft,MapsPlace place) {
        if(draft.priceText()!=null || place==null || draft.category()==Category.STAY) { return draft.priceText(); }
        return cut(place.priceText(),MAX_PRICE_TEXT_LENGTH);
    }

    private static String cut(String value,int max) {
        if(value==null || value.length()<=max) { return value; }
        return value.substring(0,max-CUT_MARK.length())+CUT_MARK;
    }
}
