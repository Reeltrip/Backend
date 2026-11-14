package com.reeltrip.infra.map;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

@Component
public class GoogleRoutesClient implements RoutesClient {
    private final BoundedHttp http;
    private final ObjectMapper mapper;
    private final String key;
    public GoogleRoutesClient(BoundedHttp http,ObjectMapper mapper,@Value("${GOOGLE_MAPS_API_KEY:}") String key) {
        this.http=http; this.mapper=mapper; this.key=key;
    }

    @Override public RouteEstimate route(RouteEstimate.Point from,RouteEstimate.Point to,Instant departure) throws IOException {
        if(key.isBlank()) { throw new IOException("경로 API 설정이 필요해요."); }
        var candidates=new ArrayList<RouteEstimate>();
        for(String mode:List.of("TRANSIT","WALK","DRIVE")) {
            if(Thread.currentThread().isInterrupted()) { throw new IOException("경로 요청이 중단됐어요."); }
            try {
                var body=mapper.createObjectNode();
                body.set("origin",mapper.valueToTree(Map.of("location",Map.of("latLng",from))));
                body.set("destination",mapper.valueToTree(Map.of("location",Map.of("latLng",to))));
                body.put("travelMode",mode); body.put("languageCode","ko");
                if(!mode.equals("WALK")) { body.put("departureTime",departure.toString()); }
                var request=HttpRequest.newBuilder(URI.create("https://routes.googleapis.com/directions/v2:computeRoutes"))
                        .timeout(Duration.ofSeconds(4)).header("X-Goog-Api-Key",key).header("Content-Type","application/json")
                        .header("X-Goog-FieldMask","routes.duration,routes.legs.steps.staticDuration,routes.legs.steps.travelMode,routes.legs.steps.transitDetails")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
                var response=http.send(request,1024*1024,Duration.ofSeconds(5));
                if(response.statusCode()!=200) { continue; }
                var root=mapper.readTree(response.body());
                for(var route:root.path("routes")) { candidates.add(parse(route,mode)); }
            } catch(IOException | RuntimeException ignored) { /* 한 교통수단이 미지원이어도 나머지를 확인해요. */ }
        }
        return candidates.stream().min(Comparator.comparing(RouteEstimate::duration)).orElseThrow(()->new IOException("경로가 없어요."));
    }

    private static RouteEstimate parse(JsonNode route,String requested) throws IOException {
        Duration duration=duration(route.path("duration").asText());
        var segments=new ArrayList<RouteEstimate.Segment>();
        for(var leg:route.path("legs")) {
            for(var step:leg.path("steps")) {
                String travel=step.path("travelMode").asText(requested);
                var transit=step.path("transitDetails");
                String mode=travel.equals("DRIVE")?"TAXI":travel;
                String line=null,stop=null;
                if(travel.equals("TRANSIT")) {
                    String vehicle=transit.path("transitLine").path("vehicle").path("type").asText();
                    mode=switch(vehicle) { case "SUBWAY","METRO_RAIL"->"SUBWAY"; case "BUS","INTERCITY_BUS","TROLLEYBUS"->"BUS";
                        case "HEAVY_RAIL","HIGH_SPEED_TRAIN","COMMUTER_TRAIN","RAIL"->"TRAIN"; case "TRAM","LIGHT_RAIL"->"TRAM"; default->"TRANSIT"; };
                    line=transit.path("transitLine").path("nameShort").asText(null);
                    if(line==null) { line=transit.path("transitLine").path("name").asText(null); }
                    stop=transit.path("stopDetails").path("arrivalStop").path("name").asText(null);
                }
                segments.add(new RouteEstimate.Segment(mode,minutes(duration(step.path("staticDuration").asText())),line,stop));
            }
        }
        if(segments.isEmpty()) { segments.add(new RouteEstimate.Segment(requested.equals("DRIVE")?"TAXI":requested,minutes(duration),null,null)); }
        return new RouteEstimate(duration,List.copyOf(segments));
    }

    private static Duration duration(String value) throws IOException {
        if(value.length()>24 || !value.matches("[0-9]+(?:\\.[0-9]{1,9})?s")) { throw new IOException("경로 시간이 잘못됐어요."); }
        try {
            long seconds=new BigDecimal(value.substring(0,value.length()-1)).setScale(0,RoundingMode.CEILING).longValueExact();
            if(seconds>604800) { throw new IOException("경로 시간이 너무 길어요."); }
            return Duration.ofSeconds(seconds);
        } catch(ArithmeticException e) { throw new IOException("경로 시간이 잘못됐어요."); }
    }
    public static int minutes(Duration duration) { return (int)((duration.toSeconds()+59)/60); }
}
