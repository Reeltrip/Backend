-- #79 마이페이지: schema.sql로 이미 만든 DB에 프로필 사진 컬럼과 문의 테이블을 더해요. 빈 DB는 schema.sql만 적용하면 돼요.
-- 기존 회원은 모두 NULL(기본 사진)이라 데이터는 바뀌지 않아요. prod ddl-auto=validate라 이 둘이 없으면 새 버전 서버가 뜨지 않아요.
ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(2048) NULL COMMENT '프로필 사진 주소 (NULL이면 기본 사진, #79)' AFTER `updated_at`;

CREATE TABLE `inquiry` (
  `inquiry_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '문의 ID',
  `user_id` BIGINT NOT NULL COMMENT '작성 회원 ID',
  `type` VARCHAR(30) NOT NULL COMMENT 'SERVICE_USAGE, ACCOUNT_LOGIN, TRAVEL_CARD_ERROR, OTHER',
  `content` VARCHAR(1000) NOT NULL COMMENT '문의 내용 (API는 300자까지 받음)',
  `wants_reply_notification` TINYINT(1) NOT NULL COMMENT '답변 알림 수신 희망 여부',
  `created_at` DATETIME NOT NULL COMMENT '접수 일시',
  PRIMARY KEY (`inquiry_id`),
  CONSTRAINT `FK_user_TO_inquiry_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='마이페이지 1:1 문의 (#79)';
