package com.reeltrip.infra.map;

import java.time.Duration;
import java.util.List;

/** encodedPolyline: 지도에 그릴 길 모양(Google encoded polyline). 받지 못했으면 null이에요. */
public record RouteEstimate(Duration duration,List<Segment> segments,String encodedPolyline) {
    /** 길 모양 없이 만드는 기존 호출을 위한 생성자예요. */
    public RouteEstimate(Duration duration,List<Segment> segments) { this(duration,segments,null); }
    public record Segment(String mode,int minutes,String lineName,String stopName) { }
    public record Point(double latitude,double longitude) { }
}
