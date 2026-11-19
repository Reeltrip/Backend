package com.reeltrip.domain.route;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import com.reeltrip.domain.place.service.PlaceRouteAccessService;
import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.infra.map.*;

@Service
public class RouteService {
    private final PlaceRouteAccessService access;
    private final RoutesClient routes;
    private final Clock clock;
    private final LinkedHashMap<Key,Cached> cache=new LinkedHashMap<>();
    public RouteService(PlaceRouteAccessService access,RoutesClient routes,Clock clock) { this.access=access; this.routes=routes; this.clock=clock; }

    public Response get(Long userId,Long from,Long to,OffsetDateTime requested) {
        var start=access.point(userId,from); var end=access.point(userId,to);
        var departure=(requested==null?clock.instant().truncatedTo(ChronoUnit.SECONDS):requested.toInstant())
                .atZone(TimeConfig.SEOUL).toOffsetDateTime();
        var key=new Key(userId,from,to,start,end,departure.toInstant().truncatedTo(ChronoUnit.MINUTES),requested==null);
        RouteEstimate estimate=null;
        if(routes.cacheable()) {
            synchronized(cache) {
                cache.entrySet().removeIf(entry->!clock.instant().isBefore(entry.getValue().expires()));
                var cached=cache.get(key); if(cached!=null) { estimate=cached.estimate(); }
            }
        }
        if(estimate==null) {
            try { estimate=routes.route(start,end,requested==null?null:requested.toInstant()); }
            catch(IOException | RuntimeException e) { throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND); }
            if(routes.cacheable()) {
                synchronized(cache) {
                    if(cache.size()>=256) { cache.remove(cache.keySet().iterator().next()); }
                    cache.put(key,new Cached(estimate,clock.instant().plusSeconds(30)));
                }
            }
        }
        return new Response(GoogleRoutesClient.minutes(estimate.duration()),departure,departure.plus(estimate.duration()),estimate.segments(),
                estimate.encodedPolyline());
    }
    /** polyline: 지도에 그릴 길 모양(Google encoded polyline). 없으면 null이고 FE는 직선으로 그려요 (#93). */
    public record Response(int totalMinutes,OffsetDateTime departAt,OffsetDateTime arriveAt,List<RouteEstimate.Segment> segments,
            String polyline) { }
    private record Key(Long userId,Long from,Long to,RouteEstimate.Point start,RouteEstimate.Point end,Instant minute,boolean immediate) { }
    private record Cached(RouteEstimate estimate,Instant expires) { }
}
