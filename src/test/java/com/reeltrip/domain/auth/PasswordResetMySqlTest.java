package com.reeltrip.domain.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.auth.dto.LoginRequest;
import com.reeltrip.domain.auth.entity.PasswordResetToken;
import com.reeltrip.domain.auth.repository.*;
import com.reeltrip.domain.auth.service.*;
import com.reeltrip.domain.user.entity.User;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.*;
import com.reeltrip.infra.mail.EmailSender;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "server.address=127.0.0.1", "spring.datasource.url=${REELTRIP_RESET_MYSQL_URL}",
        "spring.datasource.username=root", "spring.datasource.password=",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(named="REELTRIP_RESET_MYSQL_URL",
        matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reeltrip_issue14.*")
class PasswordResetMySqlTest {
    private static final String OLD="OldPassword123", NEXT="NewPassword456";
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired RefreshTokenRepository refreshes;
    @Autowired PasswordResetService service;
    @Autowired AuthService auth;
    @Autowired RefreshTokenCodec codec;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean PasswordEncoder encoder;
    @MockitoBean EmailSender mail;
    private final Map<String,String> sent=new ConcurrentHashMap<>();

    @BeforeEach void mail() {
        doAnswer(call->{sent.put(call.getArgument(0),call.getArgument(1));return null;})
                .when(mail).sendPasswordReset(anyString(),anyString(),any());
    }

    @Test @DisplayName("MySQL·실제 HTTP에서 링크를 확인·소비하고 이전 로그인과 refresh를 거부한다.")
    void httpResetAndRevocation() throws Exception {
        // given
        var user=account();
        var login=http.postForEntity("/auth/login",Map.of("email",user.getEmail(),"password",OLD),JsonNode.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String refresh=login.getBody().path("refreshToken").asText();
        // when
        var receipt=http.postForEntity("/auth/password-reset",Map.of("email",user.getEmail()),String.class);
        assertThat(receipt.getStatusCode().value()).isEqualTo(200);
        assertThat(receipt.getBody()).isNullOrEmpty();
        String token=awaitToken(user.getEmail());
        assertThat(http.postForEntity("/auth/password-reset/verify",Map.of("token",token),JsonNode.class).getStatusCode().value()).isEqualTo(200);
        // then
        assertThat(http.postForEntity("/auth/password-reset/confirm",Map.of("token",token,"newPassword",NEXT),String.class).getStatusCode().value()).isEqualTo(204);
        assertThat(http.postForEntity("/auth/refresh",Map.of("refreshToken",refresh),JsonNode.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity("/auth/login",Map.of("email",user.getEmail(),"password",OLD),JsonNode.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity("/auth/login",Map.of("email",user.getEmail(),"password",NEXT),JsonNode.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.postForEntity("/auth/password-reset/confirm",Map.of("token",token,"newPassword",OLD),JsonNode.class).getBody().path("code").asText()).isEqualTo("INVALID_RESET_TOKEN");
    }

    @Test @DisplayName("실제 MySQL에서 같은 링크를 동시에 바꿔도 한 요청만 소비에 성공한다.")
    void singleConsumption() throws Exception {
        // given
        var user=account(); service.request(user.getEmail()); String raw=sent.get(user.getEmail());
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> confirm=()->{start.await();try{service.confirm(raw,NEXT);return true;}
                catch(BusinessException error){assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_RESET_TOKEN);return false;}};
            // when
            var a=pool.submit(confirm); var b=pool.submit(confirm); start.countDown();
            // then
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(resets.findByTokenHash(PasswordResetToken.hashToken(raw)).orElseThrow().isUsed()).isTrue();
        }
    }

    @Test @DisplayName("MySQL의 이전 조회 스냅샷 뒤 로그인에서 발급된 refresh까지 재설정이 폐기한다.")
    void revokesRefreshIssuedDuringResetSnapshot() throws Exception {
        // given
        var user=account(); service.request(user.getEmail()); String raw=sent.get(user.getEmail());
        var loginLocked=new CountDownLatch(1); var releaseLogin=new CountDownLatch(1); var snapshotRead=new CountDownLatch(1);
        doAnswer(call->{loginLocked.countDown();if(!releaseLogin.await(5,TimeUnit.SECONDS)){throw new IllegalStateException("release timeout");}return call.callRealMethod();})
                .when(encoder).matches(OLD,user.getPassword());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var login=pool.submit(()->auth.login(new LoginRequest(user.getEmail(),OLD)));
            assertThat(loginLocked.await(5,TimeUnit.SECONDS)).isTrue();
            // when: 로그인은 회원 잠금을 보유하고 reset은 그 commit 전 snapshot을 먼저 만들어요.
            var reset=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                resets.userIdByHash(PasswordResetToken.hashToken(raw)).orElseThrow(); snapshotRead.countDown();
                service.confirm(raw,NEXT); return null;
            }));
            assertThat(snapshotRead.await(5,TimeUnit.SECONDS)).isTrue(); releaseLogin.countDown();
            String token=login.get(10,TimeUnit.SECONDS).refreshToken(); reset.get(10,TimeUnit.SECONDS);
            // then
            assertThat(refreshes.findByToken(codec.hash(token))).isEmpty();
            assertThatThrownBy(()->auth.refresh(token)).isInstanceOf(BusinessException.class);
        } finally { releaseLogin.countDown(); }
    }

    @Test @DisplayName("같은 계정의 동시 재요청은 MySQL 회원 잠금과 60초 제한으로 메일 한 통만 보낸다.")
    void concurrentRequestsSendOnce() throws Exception {
        // given
        var user=account(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Runnable request=()->{try{start.await();service.request(user.getEmail());}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}};
            // when
            var a=pool.submit(request);var b=pool.submit(request);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            // then
            verify(mail,times(1)).sendPasswordReset(eq(user.getEmail()),anyString(),any());
        }
    }

    private User account() { return users.save(User.create(UUID.randomUUID()+"@example.test",encoder.encode(OLD),"재설정",false,LocalDateTime.now())); }
    private String awaitToken(String email) throws InterruptedException {
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
        do{String token=sent.get(email);if(token!=null&&resets.findByTokenHash(PasswordResetToken.hashToken(token)).isPresent()){return token;}Thread.sleep(10);}while(System.nanoTime()<deadline);
        throw new AssertionError("메일 작업 commit timeout");
    }
}
