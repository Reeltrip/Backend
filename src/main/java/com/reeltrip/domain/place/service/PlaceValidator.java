package com.reeltrip.domain.place.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** hasSourceLink: AI가 만든 카드인지. 그때만 sourceUrl이 필수예요. */
    public void validate(PlaceContent content, boolean hasSourceLink) {
        Category category = content.category();
        require(category != null);
        require(hasText(content.name()) && fits(content.name(), MAX_NAME_LENGTH));

        if (category.isPlace()) {
            require(hasText(content.imageUrl()) && hasText(content.address()));
            require(content.latitude() != null && content.longitude() != null);
        } else {
            require(hasText(content.subType()) && hasText(content.applicableRegion()));
        }
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
