package com.reeltrip.infra.map;

import java.io.IOException;

public interface PlacePhotoClient {
    /** 장소의 대표 사진을 찾아요. 사진이 없으면 MapsPhoto.NONE, 외부 호출이 실패하면 IOException이에요. */
    MapsPhoto findPhoto(String googlePlaceId) throws IOException;
}
