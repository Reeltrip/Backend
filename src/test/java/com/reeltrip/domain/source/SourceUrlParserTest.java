package com.reeltrip.domain.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.reeltrip.domain.source.entity.Platform;
import com.reeltrip.domain.source.service.SourceUrlParser;

class SourceUrlParserTest {

    private final SourceUrlParser parser = new SourceUrlParser();

    @Test
    @DisplayName("대소문자와 실제 하위 도메인은 지원하고 비슷한 다른 도메인은 구분한다.")
    void platformClassificationUsesHostBoundaries() {
        // given / when / then
        assertThat(parser.parse("HTTPS://WWW.YouTube.com/shorts/demo?t=42").platform()).isEqualTo(Platform.YOUTUBE);
        assertThat(parser.parse("https://youtu.be/demo").platform()).isEqualTo(Platform.YOUTUBE);
        assertThat(parser.parse("https://www.instagram.com/reel/demo").platform()).isEqualTo(Platform.INSTAGRAM);
        assertThat(parser.parse("https://m.blog.naver.com/post").platform()).isEqualTo(Platform.NAVER_BLOG);
        assertThat(parser.parse("https://youtube.com.attacker.test/demo").platform()).isEqualTo(Platform.ETC);
        assertThat(parser.parse("https://notyoutube.com/demo").platform()).isEqualTo(Platform.ETC);
    }
}
