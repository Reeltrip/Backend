package com.reeltrip.domain.inquiry.entity;

/** 마이페이지 1:1 문의 유형 (#79). 화면 문구로 바꾸는 건 FE가 해요. */
public enum InquiryType {
    SERVICE_USAGE,      // 서비스 이용 문의
    ACCOUNT_LOGIN,      // 계정 및 로그인
    TRAVEL_CARD_ERROR,  // 여행 카드오류
    OTHER               // 기타 문의
}
