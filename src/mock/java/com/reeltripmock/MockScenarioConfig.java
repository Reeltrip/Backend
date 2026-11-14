package com.reeltripmock;

import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 가짜 서버에서만 헤더로 실패 화면을 재현해요. 실제 API의 요청 필드는 바꾸지 않아요. */
@Configuration
class MockScenarioConfig implements WebMvcConfigurer {

    private static final Set<ErrorCode> ERRORS = Set.of(
            ErrorCode.INVALID_INPUT, ErrorCode.DUPLICATE_EMAIL, ErrorCode.INVALID_EMAIL_CODE,
            ErrorCode.EMAIL_CODE_EXPIRED, ErrorCode.EMAIL_CODE_TOO_FREQUENT, ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED,
            ErrorCode.EMAIL_NOT_VERIFIED, ErrorCode.INVALID_PASSWORD_FORMAT, ErrorCode.MAIL_SEND_FAILED,
            ErrorCode.INVALID_RESET_TOKEN, ErrorCode.RESET_TOKEN_EXPIRED, ErrorCode.INVALID_LOGIN,
            ErrorCode.ROUTE_NOT_FOUND, ErrorCode.INTERNAL_SERVER_ERROR);

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String requested = request.getHeader("X-Mock-Error");
                if (requested != null) {
                    ErrorCode code;
                    try {
                        code = ErrorCode.valueOf(requested);
                    } catch (IllegalArgumentException error) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT);
                    }
                    throw new BusinessException(ERRORS.contains(code) ? code : ErrorCode.INVALID_INPUT);
                }
                return true;
            }
        }).excludePathPatterns("/mock/**", "/actuator/**");
    }
}

