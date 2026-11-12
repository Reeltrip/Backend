// BE 2가 뼈대로 먼저 만듦 (2026-11-12). BE 1이 이어서 구현
package com.reeltrip.domain.user.entity;

import java.time.LocalDateTime;

import com.reeltrip.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 회원 (ERD user). 비밀번호는 BCrypt 값이라 절대 응답에 넣지 않아요. */
@Entity
@Table(name = "user")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "is_marketing_agreed", nullable = false)
    private boolean isMarketingAgreed;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    protected User() {
    }

    private User(String email, String encodedPassword, String nickname,
                 boolean isMarketingAgreed, LocalDateTime termsAgreedAt) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.isMarketingAgreed = isMarketingAgreed;
        this.termsAgreedAt = termsAgreedAt;
    }

    /** encodedPassword는 BCrypt로 바꾼 값을 넘겨요. */
    public static User create(String email, String encodedPassword, String nickname,
                              boolean isMarketingAgreed, LocalDateTime termsAgreedAt) {
        return new User(email, encodedPassword, nickname, isMarketingAgreed, termsAgreedAt);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isMarketingAgreed() {
        return isMarketingAgreed;
    }

    public LocalDateTime getTermsAgreedAt() {
        return termsAgreedAt;
    }
}
