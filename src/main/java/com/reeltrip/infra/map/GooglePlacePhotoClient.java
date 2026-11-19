package com.reeltrip.infra.map;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

/**
 * S4-3 검색한 장소의 마커 사진이에요. 장소 ID로 사진 이름을 얻고(사진 항목만 요청), 첫 사진의 주소를 받아요.
 * 사진이 없으면 요금이 드는 두 번째 호출을 하지 않아요.
 */
@Component
public class GooglePlacePhotoClient implements PlacePhotoClient {
    /** 주소 경로에 그대로 넣는 값이라 글자 종류를 좁혀요. */
    public static final Pattern PLACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,255}");
    private static final Pattern PHOTO_NAME = Pattern.compile("places/[A-Za-z0-9_-]{1,255}/photos/[A-Za-z0-9_-]{1,1024}");
    /** 마커 사진은 지름 86px 이하예요. 고해상도 화면까지 넉넉히 덮는 크기예요. */
    private static final int MAX_WIDTH_PX = 400;
    private static final String BASE = "https://places.googleapis.com/v1/";

    private final BoundedHttp http;
    private final ObjectMapper mapper;
    private final String key;
    public GooglePlacePhotoClient(BoundedHttp http,ObjectMapper mapper,@Value("${GOOGLE_MAPS_API_KEY:}") String key) {
        this.http=http; this.mapper=mapper; this.key=key;
    }

    @Override public MapsPhoto findPhoto(String googlePlaceId) throws IOException {
        if(key.isBlank()) { throw new IOException("장소 검색 설정이 필요해요."); }
        if(googlePlaceId==null || !PLACE_ID.matcher(googlePlaceId).matches()) { throw new IOException("장소 ID가 잘못됐어요."); }
        var photo=get(BASE+"places/"+googlePlaceId,"photos").path("photos").path(0);
        String name=photo.path("name").asText("");
        if(!PHOTO_NAME.matcher(name).matches()) { return MapsPhoto.NONE; }
        String uri=get(BASE+name+"/media?maxWidthPx="+MAX_WIDTH_PX+"&skipHttpRedirect=true",null).path("photoUri").asText("");
        if(!uri.startsWith("https://")) { return MapsPhoto.NONE; }
        var attributions=new ArrayList<MapsPlace.Attribution>();
        for(var author:photo.path("authorAttributions")) {
            String display=author.path("displayName").asText("");
            if(display.isBlank()) { continue; }
            String link=author.path("uri").asText("");
            // Google은 //maps.google.com/... 처럼 프로토콜 없는 주소를 주기도 해요.
            if(link.startsWith("//")) { link="https:"+link; }
            attributions.add(new MapsPlace.Attribution(display,link.startsWith("https://")?link:null));
        }
        return new MapsPhoto(uri,List.copyOf(attributions));
    }

    private JsonNode get(String url,String fieldMask) throws IOException {
        var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).header("X-Goog-Api-Key",key).GET();
        if(fieldMask!=null) { builder.header("X-Goog-FieldMask",fieldMask); }
        var response=http.send(builder.build(),256*1024,Duration.ofSeconds(5));
        if(response.statusCode()!=200) { throw new IOException("장소 사진을 가져오지 못했어요."); }
        var root=mapper.readTree(response.body());
        if(root==null || !root.isObject()) { throw new IOException("장소 사진 응답이 잘못됐어요."); }
        return root;
    }
}
