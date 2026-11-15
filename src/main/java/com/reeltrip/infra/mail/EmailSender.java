package com.reeltrip.infra.mail;

import java.time.Duration;

/** 회원 메일 발송 경계. 실패하면 BusinessException(MAIL_SEND_FAILED)을 던져요. */
public interface EmailSender {

    /** S0-1b 가입 인증번호 메일. validFor는 메일에 "몇 분 안에 입력" 안내로 써요. */
    void sendSignupCode(String to, String code, Duration validFor);
}
