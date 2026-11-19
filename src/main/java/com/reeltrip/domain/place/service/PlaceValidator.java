package com.reeltrip.domain.place.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.reeltrip.domain.place.entity.Category;
import com.reeltrip.domain.place.entity.PlaceContent;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/**
 * 카드 필수 항목·길이 검사 (S2-1 저장, S2-2·S3-2 수정, S3-3 직접 추가). FE 검사만 믿지 않아요.
 * 실패는 모두 INVALID_INPUT. 길이 제한은 화면 규칙(50자)과 컬럼 길이를 따라서, DB 오류(500)로 새지 않게 해요.
 */
@Component
public class PlaceValidator {

    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_PRICE_TEXT_LENGTH = 50;
    static final int MAX_MEMO_LENGTH = 50;
    static final int MAX_KEY_POINT_LENGTH = 100;

    /**
     * hasSourceLink: AI가 만든 카드인지. 그때만 sourceUrl과 사진이 필수예요.
     * 직접 추가한 카드(S3-3, S4-3 숙소 검색)는 사진이 없어도 돼요. FE가 googlePlaceId로 마커 사진을 그려요 (#88).
     */
    public void validate(PlaceContent content, boolean hasSourceLink) {
        validateFormat(content, hasSourceLink);
        List<String> missing = missingRequiredFields(content);
        if (!hasSourceLink) {
            missing.remove("imageUrl");
        }
        require(missing.isEmpty());
    }

    /**
     * 카테고리별 필수 항목 중 빈 것을 JSON 필드 이름으로 돌려줘요. 장소 카드는 사진·주소·좌표, 교통·팁은 세부유형·적용지역.
     * AI 카드는 Places 보강을 못 하면 이 값들이 비어 있을 수 있어요 (S2-1). 카테고리가 없으면 빈 목록.
     */
    public List<String> missingRequiredFields(PlaceContent content) {
        List<String> missing = new ArrayList<>();
        Category category = content.category();
        if (category == null) {
            return missing;
        }
        if (category.isPlace()) {
            addIfMissing(missing, "imageUrl", hasText(content.imageUrl()));
            addIfMissing(missing, "address", hasText(content.address()));
            addIfMissing(missing, "latitude", content.latitude() != null);
            addIfMissing(missing, "longitude", content.longitude() != null);
        } else {
            addIfMissing(missing, "subType", hasText(content.subType()));
            addIfMissing(missing, "applicableRegion", hasText(content.applicableRegion()));
        }
        return missing;
    }

    /** 필수 항목이 비었는지는 빼고 카테고리·이름·길이·범위만 검사해요. */
    public void validateFormat(PlaceContent content, boolean hasSourceLink) {
        require(content.category() != null);
        require(hasText(content.name()) && fits(content.name(), MAX_NAME_LENGTH));
        require(content.latitude() == null || isValidLatitude(content.latitude().doubleValue()));
        require(content.longitude() == null || isValidLongitude(content.longitude().doubleValue()));
        if (hasSourceLink) {
            require(hasText(content.sourceUrl()));
        }

        validateKeyPoints(content.keyPoints());
        require(fits(content.priceText(), MAX_PRICE_TEXT_LENGTH));
        require(fits(content.memo(), MAX_MEMO_LENGTH));
        require(content.sourceTimestampSec() == null || content.sourceTimestampSec() >= 0);
        require(content.reviewCount() == null || content.reviewCount() >= 0);

        // 컬럼 길이 (ERD place)
        require(fits(content.address(), 255) && fits(content.googlePlaceId(), 255) && fits(content.city(), 100)
                && fits(content.imageUrl(), 2048) && fits(content.sourceUrl(), 2048)
                && fits(content.openingHours(), 100) && fits(content.accessText(), 255)
                && fits(content.subType(), 30) && fits(content.applicableRegion(), 100)
                && fits(content.cost(), 255) && fits(content.placeText(), 255));
    }

    public static boolean isValidCoordinate(double latitude, double longitude) {
        return isValidLatitude(latitude) && isValidLongitude(longitude);
    }

    private void validateKeyPoints(List<String> keyPoints) {
        if (keyPoints == null) {
            return;
        }
        require(keyPoints.size() <= Category.MAX_KEY_POINTS);
        for (String keyPoint : keyPoints) {
            require(hasText(keyPoint) && fits(keyPoint, MAX_KEY_POINT_LENGTH));
        }
    }

    private static boolean isValidLatitude(double latitude) {
        return latitude >= -90 && latitude <= 90;
    }

    private static boolean isValidLongitude(double longitude) {
        return longitude >= -180 && longitude <= 180;
    }

    private static void addIfMissing(List<String> missing, String field, boolean present) {
        if (!present) {
            missing.add(field);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean fits(String value, int maxLength) {
        return value == null || value.length() <= maxLength;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 위도·경도는 컬럼 소수 7자리에 맞춰 반올림해요 */
    static BigDecimal toCoordinate(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }
}
