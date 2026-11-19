package com.reeltrip.infra.map;

import java.util.List;

/**
 * Google 장소 사진 한 장이에요. photoUrl은 Google이 잠깐만 열어 주는 주소라 DB·Storage에 저장하지 않아요.
 * 사진이 없으면 photoUrl이 null이에요.
 */
public record MapsPhoto(String photoUrl, List<MapsPlace.Attribution> attributions) {
    public static final MapsPhoto NONE = new MapsPhoto(null, List.of());
}
