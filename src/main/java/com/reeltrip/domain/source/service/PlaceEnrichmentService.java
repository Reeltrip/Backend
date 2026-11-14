package com.reeltrip.domain.source.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.reeltrip.infra.ai.ExtractionResult;
import com.reeltrip.infra.ai.PlaceDraft;
import com.reeltrip.infra.fetcher.FetchedContent;
import com.reeltrip.infra.map.MapsPlace;
import com.reeltrip.infra.map.PlacesClient;

@Service
public class PlaceEnrichmentService {
    private final PlacesClient places;
    private final boolean enabled;
    public PlaceEnrichmentService(PlacesClient places,@Value("${app.maps.persist-enrichment:false}") boolean enabled) {
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
            drafts.add(new PlaceDraft(linkId,draft.category(),draft.name(),place==null?draft.address():place.address(),
                    place==null?draft.latitude():place.latitude(),place==null?draft.longitude():place.longitude(),
                    place==null?draft.googlePlaceId():place.googlePlaceId(),draft.city()==null?result.city():draft.city(),
                    draft.imageUrl()==null?content.thumbnailUrl():draft.imageUrl(),content.url(),draft.sourceTimestampSec(),
                    draft.keyPoints(),draft.priceText(),place==null?draft.reviewCount():place.reviewCount(),
                    place==null?draft.openingHours():hours(place),draft.accessText(),draft.memo(),draft.subType(),
                    draft.applicableRegion(),draft.cost(),draft.benefit(),draft.method(),draft.placeText(),draft.caution()));
        }
        return List.copyOf(drafts);
    }

    private static String hours(MapsPlace place) {
        String value=String.join("; ",place.openingHours());
        return value.isBlank()?null:value.substring(0,Math.min(value.length(),200));
    }
}
