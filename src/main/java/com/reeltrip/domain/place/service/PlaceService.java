package com.reeltrip.domain.place.service;

import org.springframework.stereotype.Service;

/** 요약카드 (S2-1, S3-1, S3-2, S3-3). 소유: place 팀원 */
@Service
public class PlaceService {

    /** GET /users/me의 보관함 카드 수 (BE 1이 씀) */
    public long countPlaces(Long userId) {
        return 0; // TODO: 가짜 응답, 실제 구현으로 교체
    }
}
