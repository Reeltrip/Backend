package com.reeltrip.infra.map;

import java.io.IOException;
import java.time.Instant;
import org.springframework.lang.Nullable;

public interface RoutesClient {
    /** null은 제공자에 출발 시각을 지정하지 않고 요청 시점의 현재 경로를 조회해요. */
    RouteEstimate route(RouteEstimate.Point from,RouteEstimate.Point to,@Nullable Instant departure) throws IOException;
    default boolean cacheable() { return false; }
}
