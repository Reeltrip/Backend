package com.reeltrip.infra.mail;

import java.io.UnsupportedEncodingException;
import java.time.Duration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;

/** SMTP(기본 Gmail)로 보내요. 시간 제한은 application.yml의 mail.smtp.*timeout(5초)이에요. */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    // 받은편지함에 주소 대신 보이는 이름이에요.
    private static final String SENDER_NAME = "Wayvi";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mailSender,
                           @Value("${MAIL_FROM:${spring.mail.username:}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendSignupCode(String to, String code, Duration validFor) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from.isBlank()) {
            log.warn("메일 설정(MAIL_USERNAME·MAIL_PASSWORD)이 없어 인증번호를 보내지 못했어요.");
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
        SignupCodeMail mail = SignupCodeMail.of(code, validFor);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            sender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            // 받는 주소·인증번호는 로그에 남기지 않아요.
            log.warn("인증번호 메일 발송 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED, e);
        }
    }
}
