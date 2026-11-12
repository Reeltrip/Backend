// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.global.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reeltrip.global.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization: Bearer {token}을 읽어 로그인 사용자를 넣어요.
 * 토큰을 보냈는데 잘못됐거나 만료됐으면 열린 주소라도 401을 내려 FE가 토큰을 다시 받게 해요.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorWriter errorWriter) {
        this.jwtProvider = jwtProvider;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Long> userId = jwtProvider.parseUserId(header.substring(BEARER_PREFIX.length()));
        if (userId.isEmpty()) {
            errorWriter.write(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(userId.get(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
