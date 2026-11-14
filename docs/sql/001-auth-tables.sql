-- 기존 user 테이블이 있고 인증 3개 테이블만 없을 때 수동 적용해요.
-- IF NOT EXISTS는 이미 있는 컬럼·제약을 수정하지 않아요. 적용 전 SHOW CREATE TABLE로 확인해요.
CREATE TABLE IF NOT EXISTS `email_code` (
  `email_code_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '인증번호 ID',
  `email` VARCHAR(255) NOT NULL COMMENT '인증할 이메일',
  `code` CHAR(6) NOT NULL COMMENT '6자리 인증번호',
  `expires_at` DATETIME NOT NULL COMMENT '만료 일시 (발송 후 5분)',
  `is_verified` TINYINT(1) NOT NULL COMMENT '확인 완료 여부',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '틀린 입력 횟수 (5회면 무효)',
  `created_at` DATETIME NOT NULL COMMENT '발송 일시',
  PRIMARY KEY (`email_code_id`),
  INDEX `idx_email_code_email_created` (`email`, `created_at`)
) COMMENT='이메일 인증번호';

CREATE TABLE IF NOT EXISTS `password_reset_token` (
  `password_reset_token_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `token` VARCHAR(64) NOT NULL COMMENT '재설정 링크 토큰의 SHA-256 해시 (UNIQUE)',
  `expires_at` DATETIME NOT NULL COMMENT '만료 일시',
  `used_at` DATETIME NULL COMMENT '사용 일시',
  `created_at` DATETIME NOT NULL COMMENT '발급 일시',
  PRIMARY KEY (`password_reset_token_id`),
  CONSTRAINT `uk_password_reset_token_token` UNIQUE (`token`),
  CONSTRAINT `FK_user_TO_password_reset_token_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='비밀번호 재설정 토큰';

CREATE TABLE IF NOT EXISTS `refresh_token` (
  `refresh_token_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `token` VARCHAR(512) NOT NULL COMMENT '리프레시 토큰 (UNIQUE)',
  `expires_at` DATETIME NOT NULL COMMENT '만료 일시',
  `created_at` DATETIME NOT NULL COMMENT '발급 일시',
  PRIMARY KEY (`refresh_token_id`),
  CONSTRAINT `uk_refresh_token_token` UNIQUE (`token`),
  CONSTRAINT `FK_user_TO_refresh_token_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='리프레시 토큰';
