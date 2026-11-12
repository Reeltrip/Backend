// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 로그인한 회원 id(Long)를 넣어요.
 * required = false면 로그인 안 했을 때 null (읽기 전용 공유 계획 조회처럼 비로그인도 여는 API용).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {

    boolean required() default true;
}
