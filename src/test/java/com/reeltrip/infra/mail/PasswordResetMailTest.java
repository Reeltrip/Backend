package com.reeltrip.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetMailTest {

    private static final String URL = "https://front.example.test/password-reset?token=" + "A".repeat(43);

    @Test
    @DisplayName("재설정 메일 HTML은 가입 메일과 같은 로고 머리에 재설정 링크 버튼과 유효 시간을 담는다.")
    void htmlHasLogoButtonAndValidity() {
        // given / when
        PasswordResetMail mail = PasswordResetMail.of(URL, Duration.ofMinutes(30));

        // then
        assertThat(mail.subject()).isEqualTo("[Wayvi] 비밀번호 재설정");
        assertThat(mail.html())
                .startsWith("<!DOCTYPE html>")
                .contains("src=\"cid:" + MailLayout.LOGO_CONTENT_ID + "\"")
                .contains("href=\"" + URL + "\"")
                .contains("30분")
                .doesNotContain("%s");
    }

    @Test
    @DisplayName("HTML을 못 여는 메일 앱에서도 재설정 링크와 유효 시간을 글자로 볼 수 있다.")
    void textHasLinkAndValidity() {
        // given / when
        PasswordResetMail mail = PasswordResetMail.of(URL, Duration.ofMinutes(30));

        // then
        assertThat(mail.text()).contains(URL).contains("30분").doesNotContain("<");
    }

    @Test
    @DisplayName("링크에 HTML 특수 문자가 있어도 속성 밖으로 새지 않게 바꿔 넣는다.")
    void escapesLinkInHtml() {
        // given / when
        PasswordResetMail mail = PasswordResetMail.of("https://front.example.test/a\"><b>x", Duration.ofMinutes(30));

        // then
        assertThat(mail.html()).doesNotContain("\"><b>x").contains("&quot;&gt;&lt;b&gt;x");
    }
}
