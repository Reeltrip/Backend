// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 오늘 날짜·현재 시각은 LocalDate.now() 대신 이 Clock으로 구해요.
 * 테스트에서 @MockitoBean / @TestConfiguration으로 바꿔 끼우면 "다가오는 여행" 같은 계산을 고정할 수 있어요.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SEOUL);
    }
}
