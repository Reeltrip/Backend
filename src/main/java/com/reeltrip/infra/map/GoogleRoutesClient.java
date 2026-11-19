package com.reeltrip.infra.map;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
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
    private final Clock clock;
    /** 응답 크기를 막는 한도예요. 도시 안 구간은 보통 수백~수천 자예요. */
    private static final int MAX_POLYLINE_LENGTH=20000;
    /**
     * 이 시간 안에 걸어갈 수 있으면 택시·대중교통이 더 빨라도 걷는 길을 골라요 (#95).
     * 일본은 대중교통이 안 나와 500m 구간도 택시로 골라지고, 지도 선이 일방통행 길을 따라 2~3배로 빙 돌았어요.
     */
    private static final Duration MAX_PREFERRED_WALK=Duration.ofMinutes(15);
    public GoogleRoutesClient(BoundedHttp http,ObjectMapper mapper,@Value("${GOOGLE_MAPS_API_KEY:}") String key,Clock clock) {
        this.http=http; this.mapper=mapper; this.key=key; this.clock=clock;
    }

    @Override public RouteEstimate route(RouteEstimate.Point from,RouteEstimate.Point to,Instant departure) throws IOException {
        if(key.isBlank()) { throw new IOException("경로 API 설정이 필요해요."); }
        var candidates=new ArrayList<RouteEstimate>();
        RouteEstimate walk=null;
        for(String mode:List.of("TRANSIT","WALK","DRIVE")) {
            if(Thread.currentThread().isInterrupted()) { throw new IOException("경로 요청이 중단됐어요."); }
            // TRANSIT만 과거 시각을 허용해요. 앞선 호출 중 지난 시각을 DRIVE에 보내지 않아요.
            if(mode.equals("DRIVE") && departure!=null && !departure.isAfter(clock.instant())) { continue; }
            try {
                var body=mapper.createObjectNode();
                body.set("origin",mapper.valueToTree(Map.of("location",Map.of("latLng",from))));
                body.set("destination",mapper.valueToTree(Map.of("location",Map.of("latLng",to))));
                body.put("travelMode",mode); body.put("languageCode","ko");
                if(!mode.equals("WALK") && departure!=null) { body.put("departureTime",departure.toString()); }
                // 차량은 출발 시각을 줄 때 교통 반영이 없으면 Google이 요청을 거절해요(TRAFFIC_UNAWARE + 시각 = 400).
                if(mode.equals("DRIVE") && departure!=null) { body.put("routingPreference","TRAFFIC_AWARE"); }
                var request=HttpRequest.newBuilder(URI.create("https://routes.googleapis.com/directions/v2:computeRoutes"))
                        .timeout(Duration.ofSeconds(4)).header("X-Goog-Api-Key",key).header("Content-Type","application/json")
                        .header("X-Goog-FieldMask","routes.duration,routes.polyline.encodedPolyline,routes.legs.steps.staticDuration,routes.legs.steps.travelMode,routes.legs.steps.transitDetails")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
                var response=http.send(request,1024*1024,Duration.ofSeconds(5));
                if(response.statusCode()!=200) { continue; }
                var root=mapper.readTree(response.body());
                for(var route:root.path("routes")) {
                    var estimate=parse(route,mode);
                    candidates.add(estimate);
                    if(mode.equals("WALK") && (walk==null || estimate.duration().compareTo(walk.duration())<0)) { walk=estimate; }
                }
            } catch(IOException | RuntimeException ignored) { /* 한 교통수단이 미지원이어도 나머지를 확인해요. */ }
        }
        if(walk!=null && walk.duration().compareTo(MAX_PREFERRED_WALK)<=0) { return walk; }
        return candidates.stream().min(Comparator.comparing(RouteEstimate::duration)).orElseThrow(()->new IOException("경로가 없어요."));
    }

    private static RouteEstimate parse(JsonNode route,String requested) throws IOException {
        Duration duration=duration(route.path("duration").asText());
        var parts=new ArrayList<Part>();
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
                long seconds=duration(step.path("staticDuration").asText()).toSeconds();
                boolean isRide=travel.equals("TRANSIT");
                // Google은 회전·직진마다 단계를 나눠요. 화면 구간 막대에는 이어진 도보·택시를 한 구간으로 보여요. 탈것은 노선마다 따로 둬요.
                Part last=parts.isEmpty()?null:parts.getLast();
                if(!isRide && last!=null && !last.isRide() && last.mode().equals(mode)) {
                    parts.set(parts.size()-1,new Part(mode,last.seconds()+seconds,null,null,false));
                } else {
                    parts.add(new Part(mode,seconds,line,stop,isRide));
                }
            }
        }
        if(parts.isEmpty()) { parts.add(new Part(requested.equals("DRIVE")?"TAXI":requested,duration.toSeconds(),null,null,false)); }
        int[] minutes=splitMinutes(parts,minutes(duration));
        var segments=new ArrayList<RouteEstimate.Segment>();
        for(int i=0;i<parts.size();i++) {
            var part=parts.get(i);
            segments.add(new RouteEstimate.Segment(part.mode(),minutes[i],part.lineName(),part.stopName()));
        }
        return new RouteEstimate(duration,List.copyOf(segments),polyline(route));
    }

    /**
     * S4-7 계획 보기 지도에서 동선을 실제 길을 따라 그릴 때 써요. 소요시간과 같은 호출에서 받아서 호출 횟수는 그대로예요.
     * encoded polyline은 ASCII 63(?)~126(~) 글자만 써요. 모양이 다르면 지어내지 않고 비워요.
     */
    private static String polyline(JsonNode route) {
        String value=route.path("polyline").path("encodedPolyline").asText("");
        return value.matches("[?-~]{1,"+MAX_POLYLINE_LENGTH+"}")?value:null;
    }

    private record Part(String mode,long seconds,String lineName,String stopName,boolean isRide) { }

    /**
     * 총 소요시간(분)을 구간 길이 비율로 나눠요. 단계 시간은 교통 반영 전 값이라 총 시간과 다를 수 있어서,
     * 구간 분의 합이 화면의 총 소요시간과 같아지도록 맞추고, 짧은 구간도 막대에 보이게 1분 이상으로 둬요.
     */
    private static int[] splitMinutes(List<Part> parts,int totalMinutes) {
        int[] result=new int[parts.size()];
        if(parts.size()==1) { result[0]=totalMinutes; return result; }
        long sum=parts.stream().mapToLong(Part::seconds).sum();
        int assigned=0;
        for(int i=0;i<parts.size();i++) {
            double exact=sum==0?(double)totalMinutes/parts.size():(double)parts.get(i).seconds()*totalMinutes/sum;
            result[i]=Math.max(1,(int)Math.round(exact));
            assigned+=result[i];
        }
        // 반올림으로 생긴 차이는 가장 긴 구간에서 맞춰요. 모두 1분이면 더 줄이지 않아요.
        while(assigned!=totalMinutes) {
            int longest=0;
            for(int i=1;i<result.length;i++) { if(result[i]>result[longest]) { longest=i; } }
            if(assigned>totalMinutes && result[longest]<=1) { break; }
            int step=assigned<totalMinutes?1:-1;
            result[longest]+=step; assigned+=step;
        }
        return result;
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
