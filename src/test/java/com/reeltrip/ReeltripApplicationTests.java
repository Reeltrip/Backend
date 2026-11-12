package com.reeltrip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReeltripApplicationTests {

    @Test
    @DisplayName("H2 테스트 DB로 서버가 뜨고 모든 엔티티 테이블이 만들어진다.")
    void contextLoads() {
    }
}
