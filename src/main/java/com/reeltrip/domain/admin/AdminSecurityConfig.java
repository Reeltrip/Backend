package com.reeltrip.domain.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.JwtAuthenticationFilter;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.global.security.SecurityErrorWriter;

@Configuration
public class AdminSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurity(HttpSecurity http, AdminAccess access,
                                             JwtProvider jwt, SecurityErrorWriter errors) throws Exception {
        return http.securityMatcher("/admin", "/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 화면 껍데기에는 데이터·인증정보가 없어요. 데이터 요청은 매번 서버에서 검사해요.
                        .requestMatchers(HttpMethod.GET, "/admin", "/admin/", "/admin/index.html",
                                "/admin/admin.css", "/admin/admin.js", "/admin/sample-place.svg").permitAll()
                        .requestMatchers("/admin/api/**").access((authentication, context) -> access.decision(authentication.get()))
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> errors.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) -> errors.write(response, ErrorCode.ADMIN_FORBIDDEN)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; script-src 'self'; "
                                + "style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; "
                                + "form-action 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
