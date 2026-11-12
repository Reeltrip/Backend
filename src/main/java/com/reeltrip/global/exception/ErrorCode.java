// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 실패 응답의 code와 message. code(이름)는 FE가 분기에 쓰므로 바꾸지 않아요.
 * 문구는 docs/api-spec.md 에러 코드 표 그대로예요. "(제안)" 문구는 FE와 확정 전이에요.
 */
public enum ErrorCode {

    // 공통 (BE 1 소유)
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."), // (제안) 명세에 문구 없음
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."), // (제안)
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요."), // (제안)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요."), // (제안)

    // BE 1: 인증·링크
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요. 로그인해 주세요."),
    INVALID_EMAIL_CODE(HttpStatus.BAD_REQUEST, "인증번호를 다시 확인해 주세요."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 시간이 지났어요."),
    EMAIL_CODE_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 받아 주세요."),
    EMAIL_CODE_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "인증번호를 다시 받아 주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증을 먼저 해 주세요."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "영문·숫자 포함 8자 이상으로 입력해 주세요."),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "링크가 올바르지 않거나 이미 사용됐어요."),
    RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "링크 유효시간이 지났어요. 비밀번호 찾기를 다시 해 주세요."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해주세요"),
    // 링크별 실패 이유로 GET /sources/{sourceId}에 담겨 내려가요.
    SOURCE_FETCH_FAILED(HttpStatus.BAD_REQUEST, "링크가 비공개이거나 지원하지 않는 형식일 수 있어요."),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "경로 정보를 불러오지 못했어요"),

    // BE 2: 카드·계획
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
