package com.reeltrip.infra.map;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

@Component
public class GooglePlacesClient implements PlacesClient {
    private final BoundedHttp http;
    private final ObjectMapper mapper;
    private final String key;
    public GooglePlacesClient(BoundedHttp http,ObjectMapper mapper,@Value("${GOOGLE_MAPS_API_KEY:}") String key) {
        this.http=http; this.mapper=mapper; this.key=key;
    }

    @Override public List<MapsPlace> search(String query) throws IOException {
        if(key.isBlank()) { throw new IOException("장소 검색 설정이 필요해요."); }
        if(query==null || query.isBlank() || query.length()>200) { throw new IOException("검색어가 잘못됐어요."); }
        var request=HttpRequest.newBuilder(URI.create("https://places.googleapis.com/v1/places:searchText"))
                .timeout(Duration.ofSeconds(4)).header("X-Goog-Api-Key",key).header("Content-Type","application/json")
                .header("X-Goog-FieldMask","places.id,places.displayName,places.formattedAddress,places.location,places.userRatingCount,places.regularOpeningHours.weekdayDescriptions,places.googleMapsUri,places.attributions,places.priceRange,places.priceLevel")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("textQuery",query,"languageCode","ko","maxResultCount",20))))
                .build();
        var response=http.send(request,1024*1024,Duration.ofSeconds(5));
        if(response.statusCode()!=200) { throw new IOException("장소를 검색하지 못했어요."); }
        var root=mapper.readTree(response.body());
        if(root==null || !root.isObject() || (root.has("places") && !root.path("places").isArray())) { throw new IOException("장소 응답이 잘못됐어요."); }
        var places=new ArrayList<MapsPlace>();
        for(var place:root.path("places")) {
            String id=place.path("id").asText(), name=place.path("displayName").path("text").asText();
            var location=place.path("location");
            if(id.isBlank() || name.isBlank() || !location.path("latitude").isNumber() || !location.path("longitude").isNumber()) { continue; }
            double lat=location.path("latitude").asDouble(),lng=location.path("longitude").asDouble();
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180) { continue; }
            var hours=new ArrayList<String>();
            for(var hour:place.path("regularOpeningHours").path("weekdayDescriptions")) { if(hour.isTextual()) { hours.add(hour.asText()); } }
            var attributions=new ArrayList<MapsPlace.Attribution>();
            for(var attribution:place.path("attributions")) {
                String uri=attribution.path("providerUri").asText();
                attributions.add(new MapsPlace.Attribution(attribution.path("provider").asText(),
                        uri.startsWith("https://") || uri.startsWith("http://")?uri:null));
            }
            String mapUri=place.path("googleMapsUri").asText(null);
            if(mapUri!=null && !mapUri.startsWith("https://")) { mapUri=null; }
            places.add(new MapsPlace(id,name,place.path("formattedAddress").asText(null),lat,lng,
                    place.path("userRatingCount").canConvertToInt()?place.path("userRatingCount").asInt():null,
                    List.copyOf(hours),mapUri,List.copyOf(attributions),priceText(place)));
            if(places.size()==20) { break; }
        }
        return List.copyOf(places);
    }

    /**
     * 가격 범위(예: ¥1,000~2,000)를 먼저 쓰고, 없으면 가격 수준(예: 가격대 보통)을 써요.
     * 두 항목은 리뷰 수·영업시간과 같은 요금 구간이라 요청 단가가 늘지 않아요.
     * 값이 이상하면 지어내지 않고 null로 둬요.
     */
    private static String priceText(JsonNode place) {
        var range=place.path("priceRange");
        var start=range.path("startPrice");
        Long from=units(start);
        if(from!=null) {
            String currency=start.path("currencyCode").asText("");
            var end=range.path("endPrice");
            if(end.isMissingNode() || end.isNull()) { return symbol(currency)+String.format("%,d",from)+"~"; }
            Long to=units(end);
            if(to!=null && to>=from && currency.equals(end.path("currencyCode").asText(""))) {
                return symbol(currency)+String.format("%,d",from)+"~"+String.format("%,d",to);
            }
            return null;
        }
        if(range.has("startPrice")) { return null; }
        return switch(place.path("priceLevel").asText("")) {
            case "PRICE_LEVEL_FREE" -> "무료";
            case "PRICE_LEVEL_INEXPENSIVE" -> "가격대 낮음";
            case "PRICE_LEVEL_MODERATE" -> "가격대 보통";
            case "PRICE_LEVEL_EXPENSIVE" -> "가격대 높음";
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> "가격대 매우 높음";
            default -> null;
        };
    }

    /** Google Money의 units는 문자열로 온 정수예요. 음수·숫자 아님·통화 코드 형식 오류는 버려요. */
    private static Long units(JsonNode money) {
        if(!money.path("currencyCode").asText("").matches("[A-Z]{3}")) { return null; }
        String units=money.path("units").asText("");
        if(!units.matches("[0-9]{1,12}")) { return null; }
        return Long.valueOf(units);
    }

    private static String symbol(String currency) {
        return switch(currency) {
            case "JPY" -> "¥";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            default -> currency+" ";
        };
    }
}
