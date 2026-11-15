package com.reeltrip.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SignupCodeMailTest {

    @Test
    void 제목에_서비스_이름이_들어간다() {
        SignupCodeMail mail = SignupCodeMail.of("012345", Duration.ofMinutes(5));

        assertThat(mail.subject()).isEqualTo("[Wayvi] 회원가입 인증번호");
    }

    @Test
    void HTML_본문에_인증번호와_유효시간이_들어간다() {
        SignupCodeMail mail = SignupCodeMail.of("012345", Duration.ofMinutes(5));

        assertThat(mail.html())
                .startsWith("<!DOCTYPE html>")
                .contains("012345")
                .contains("5분")
                .doesNotContain("%s")
                .doesNotContain("%d");
    }

    @Test
    void HTML을_못_여는_메일_앱을_위한_글자_본문도_있다() {
        SignupCodeMail mail = SignupCodeMail.of("987654", Duration.ofMinutes(5));

        assertThat(mail.text())
                .contains("987654")
                .contains("5분")
                .doesNotContain("<");
    }

    @Test
    void 인증번호가_아닌_값은_받지_않는다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> SignupCodeMail.of("<b>1</b>", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
