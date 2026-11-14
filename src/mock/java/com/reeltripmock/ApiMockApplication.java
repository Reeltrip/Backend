package com.reeltripmock;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;

import com.reeltrip.global.config.CorsConfig;
import com.reeltrip.global.config.TimeConfig;
import com.reeltrip.global.exception.GlobalExceptionHandler;
import com.reeltrip.global.security.SecurityErrorWriter;

/** FE 계약 확인용 별도 서버. main 소스와 운영 JAR에는 포함하지 않아요. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class})
@ComponentScan
@Import({CorsConfig.class, TimeConfig.class, GlobalExceptionHandler.class, SecurityErrorWriter.class})
@Profile("!prod")
public class ApiMockApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication application = new SpringApplication(ApiMockApplication.class);
        application.setAdditionalProfiles("api-mock");
        application.addInitializers(context -> {
            if (context.getEnvironment().acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("가짜 API 서버는 prod 프로필로 실행할 수 없어요.");
            }
        });
        application.run(args);
    }
}
