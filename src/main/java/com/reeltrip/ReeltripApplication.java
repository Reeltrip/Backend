package com.reeltrip;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReeltripApplication {

    public static void main(String[] args) {
        // 날짜·시간 응답을 +09:00으로 맞추기 위해 서버 기본 시간대를 서울로 둬요.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(ReeltripApplication.class, args);
    }
}
