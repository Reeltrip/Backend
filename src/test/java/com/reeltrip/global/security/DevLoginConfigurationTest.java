package com.reeltrip.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.reeltrip.domain.user.repository.UserRepository;

class DevLoginConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalDevLoginController.class)
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(JwtProvider.class, () -> mock(JwtProvider.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("local 프로필만 켜서는 비밀번호 없는 개발용 로그인이 생기지 않는다.")
    void localRequiresExplicitOptIn() {
        // given / when / then
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).doesNotHaveBean(LocalDevLoginController.class));
    }

    @Test
    @DisplayName("local에서 명시적으로 허용했을 때만 개발용 로그인이 생긴다.")
    void localOptInEnablesDevLogin() {
        // given / when / then
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("app.auth.dev-login.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LocalDevLoginController.class));
    }

    @Test
    @DisplayName("prod가 함께 켜지면 허용 설정이 있어도 개발용 로그인이 생기지 않는다.")
    void prodAlwaysDisablesDevLogin() {
        // given / when / then
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "prod"))
                .withPropertyValues("app.auth.dev-login.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(LocalDevLoginController.class));
    }
}
