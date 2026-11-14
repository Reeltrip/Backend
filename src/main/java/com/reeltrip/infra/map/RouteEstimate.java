package com.reeltrip.infra.map;

import java.time.Duration;
import java.util.List;

public record RouteEstimate(Duration duration,List<Segment> segments) {
    public record Segment(String mode,int minutes,String lineName,String stopName) { }
    public record Point(double latitude,double longitude) { }
}
