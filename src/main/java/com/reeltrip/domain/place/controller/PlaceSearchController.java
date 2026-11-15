package com.reeltrip.domain.place.controller;

import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.LoginUser;
import com.reeltrip.infra.map.MapsPlace;
import com.reeltrip.infra.map.PlacesClient;

@RestController
public class PlaceSearchController {
    private final PlacesClient client;
    private final UserRepository users;
    public PlaceSearchController(PlacesClient client,UserRepository users) { this.client=client; this.users=users; }

    @GetMapping("/place-search")
    public ResponseEntity<SearchResponse> search(@LoginUser Long userId,@RequestParam String query) {
        if(!users.existsById(userId)) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
        if(query.isBlank() || query.length()>200) { throw new BusinessException(ErrorCode.INVALID_INPUT); }
        try {
            var results=client.search(query.strip()).stream().map(place->new SearchPlace(place.googlePlaceId(),place.name(),place.address(),
                    place.latitude(),place.longitude(),null,place.googleMapsUri(),place.attributions())).toList();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SearchResponse(results,"Google Maps"));
        } catch(IOException | RuntimeException e) { throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED); }
    }
    public record SearchResponse(List<SearchPlace> places,String attributionText) { }
    public record SearchPlace(String googlePlaceId,String name,String address,Double latitude,Double longitude,String imageUrl,
            String googleMapsUri,List<MapsPlace.Attribution> attributions) { }
}
