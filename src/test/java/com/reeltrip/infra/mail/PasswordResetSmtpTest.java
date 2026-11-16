package com.reeltrip.infra.mail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.*;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetSmtpTest {
    private static final String TOKEN = "A".repeat(43);

    @Test @DisplayName("기존 SMTP 발신자로 신뢰한 FE 주소의 30분 재설정 링크를 보낸다.")
    void sendsTrustedLink() throws Exception {
        // given
        var smtp=mock(JavaMailSender.class);
        var message=new MimeMessage(Session.getInstance(new Properties()));
        when(smtp.createMimeMessage()).thenReturn(message);
        // when
        sender(smtp,"https://front.example.test/","prod").sendPasswordReset("receiver@example.test",TOKEN,Duration.ofMinutes(30));
        // then
        verify(smtp).send(message);
        assertThat(message.getSubject()).isEqualTo("[Wayvi] 비밀번호 재설정");
        assertThat(((InternetAddress)message.getFrom()[0]).getPersonal()).isEqualTo("Wayvi");
        // 글자 본문과 HTML 본문 모두에 링크와 유효 시간이 있어야 해요.
        assertThat(texts(message)).hasSize(2).allSatisfy(body->
                assertThat(body).contains("https://front.example.test/password-reset?token="+TOKEN,"30분"));
    }

    @Test @DisplayName("SMTP 예외 속 링크·계정은 로그나 상위 예외에 노출하지 않는다.")
    void sanitizedFailure(CapturedOutput output) {
        // given
        var smtp=mock(JavaMailSender.class);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("receiver@example.test "+TOKEN)).when(smtp).send(any(MimeMessage.class));
        // when / then
        assertThatThrownBy(()->sender(smtp,"https://front.example.test","prod").sendPasswordReset("receiver@example.test",TOKEN,Duration.ofMinutes(30)))
                .isInstanceOfSatisfying(BusinessException.class,error->{
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MAIL_SEND_FAILED);
                    assertThat(error.getCause()).isNull();
                });
        assertThat(output.getAll()).doesNotContain(TOKEN,"receiver@example.test");
    }

    @ParameterizedTest @ValueSource(strings={"http://front.example.test","https://user@front.example.test","https://front.example.test?next=bad","https://front.example.test#bad","http://localhost:5173","https://localhost"})
    @DisplayName("운영의 HTTPS 공개 FE 주소가 아니거나 query·인증 정보가 있으면 메일을 보내지 않는다.")
    void rejectsUntrustedBase(String base) {
        // given
        var smtp=mock(JavaMailSender.class);
        // when / then
        assertThatThrownBy(()->sender(smtp,base,"prod").sendPasswordReset("receiver@example.test",TOKEN,Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class);
        verify(smtp,never()).send(any(MimeMessage.class));
    }

    @Test @DisplayName("로컬 개발에서는 localhost HTTP 재설정 화면을 쓸 수 있다.")
    void allowsLocalDevelopment() {
        // given
        var smtp=mock(JavaMailSender.class);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        // when
        sender(smtp,"http://localhost:5173","local").sendPasswordReset("receiver@example.test",TOKEN,Duration.ofMinutes(30));
        // then
        verify(smtp).send(any(MimeMessage.class));
    }

    private static java.util.List<String> texts(jakarta.mail.Part part) throws Exception {
        var found=new java.util.ArrayList<String>();
        // 보내기 전 메시지는 머리글이 아직 안 정해져서 내용 모양으로 먼저 나눠요.
        if (part.getContent() instanceof jakarta.mail.Multipart multipart) {
            for (int i=0;i<multipart.getCount();i++) { found.addAll(texts(multipart.getBodyPart(i))); }
        } else if (part.getContent() instanceof String text) { found.add(text); }
        return found;
    }

    private SmtpEmailSender sender(JavaMailSender smtp,String base,String profile) {
        var factory=new StaticListableBeanFactory(); factory.addBean("smtp",smtp);
        var environment=new MockEnvironment(); environment.setActiveProfiles(profile);
        return new SmtpEmailSender(factory.getBeanProvider(JavaMailSender.class),"sender@example.test",base,environment);
    }
}
