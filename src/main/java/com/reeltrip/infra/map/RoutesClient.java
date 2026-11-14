package com.reeltrip.infra.map;

import java.io.IOException;
import java.time.Instant;

public interface RoutesClient {
    RouteEstimate route(RouteEstimate.Point from,RouteEstimate.Point to,Instant departure) throws IOException;
    default boolean cacheable() { return false; }
}
