CREATE TABLE `user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
  `email` VARCHAR(255) NOT NULL COMMENT '이메일 (UNIQUE)',
  `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt로 암호화한 비밀번호',
  `nickname` VARCHAR(30) NOT NULL COMMENT '닉네임 (공유 시 함께하는 사람에게 보이는 이름)',
  `is_marketing_agreed` TINYINT(1) NOT NULL COMMENT '마케팅 정보 수신 동의 (선택 약관)',
  `terms_agreed_at` DATETIME NOT NULL COMMENT '필수 약관 2개 동의 일시',
  `created_at` DATETIME NOT NULL COMMENT '생성 일시',
  `updated_at` DATETIME NOT NULL COMMENT '수정 일시',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `uk_user_email` UNIQUE (`email`)
) COMMENT='회원';

CREATE TABLE `email_code` (
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

CREATE TABLE `password_reset_token` (
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

CREATE TABLE `refresh_token` (
  `refresh_token_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `token` VARCHAR(512) NOT NULL COMMENT '리프레시 토큰 (UNIQUE)',
  `expires_at` DATETIME NOT NULL COMMENT '만료 일시',
  `created_at` DATETIME NOT NULL COMMENT '발급 일시',
  PRIMARY KEY (`refresh_token_id`),
  CONSTRAINT `uk_refresh_token_token` UNIQUE (`token`),
  CONSTRAINT `FK_user_TO_refresh_token_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='리프레시 토큰';

CREATE TABLE `source` (
  `source_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '분석 요청 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `status` VARCHAR(20) NOT NULL COMMENT 'PENDING, ANALYZING, DONE, FAILED (링크가 모두 끝나면 종료)',
  `title` VARCHAR(50) NULL COMMENT '수집 내역 제목 (분석이 끝나면 채움)',
  `created_at` DATETIME NOT NULL COMMENT '생성 일시',
  `updated_at` DATETIME NOT NULL COMMENT '수정 일시',
  PRIMARY KEY (`source_id`),
  INDEX `idx_source_user_created` (`user_id`, `created_at`),
  CONSTRAINT `FK_user_TO_source_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='링크 분석 요청';

CREATE TABLE `source_link` (
  `source_link_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '링크 ID',
  `source_id` BIGINT NOT NULL COMMENT '분석 요청 ID',
  `url` VARCHAR(2048) NOT NULL COMMENT '붙여넣은 링크',
  `platform` VARCHAR(20) NOT NULL COMMENT 'YOUTUBE, INSTAGRAM, NAVER_BLOG, ETC',
  `status` VARCHAR(20) NOT NULL COMMENT 'PENDING, ANALYZING, DONE, FAILED',
  `fail_code` VARCHAR(50) NULL COMMENT '실패 에러 코드',
  `fail_message` VARCHAR(255) NULL COMMENT '실패 안내 문구',
  `draft_json` TEXT NULL COMMENT '카드 초안 목록 JSON (보관함 저장 전)',
  `created_at` DATETIME NOT NULL COMMENT '생성 일시',
  `updated_at` DATETIME NOT NULL COMMENT '수정 일시',
  PRIMARY KEY (`source_link_id`),
  CONSTRAINT `FK_source_TO_source_link_1` FOREIGN KEY (`source_id`) REFERENCES `source` (`source_id`)
) COMMENT='분석 링크';

CREATE TABLE `place` (
  `place_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '요약카드 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `source_link_id` BIGINT NULL COMMENT '링크 ID (직접 추가 카드는 NULL)',
  `category` VARCHAR(20) NOT NULL COMMENT 'FOOD, CAFE, STAY, SIGHTSEEING, ACTIVITY, SHOPPING, TRANSPORT, TIP',
  `name` VARCHAR(50) NOT NULL COMMENT '장소명 또는 교통·팁 제목',
  `address` VARCHAR(255) NULL COMMENT '현지 주소 (장소 카드 필수)',
  `latitude` DECIMAL(10,7) NULL COMMENT '위도 (장소 카드 필수)',
  `longitude` DECIMAL(10,7) NULL COMMENT '경도 (장소 카드 필수)',
  `google_place_id` VARCHAR(255) NULL COMMENT 'Google Maps 장소 ID',
  `city` VARCHAR(100) NULL COMMENT '도시 (계획 생성 시 보관함 카드를 불러오는 기준)',
  `image_url` VARCHAR(2048) NULL COMMENT '대표 사진 (장소 카드 필수)',
  `source_url` VARCHAR(2048) NULL COMMENT '원본 링크 (AI 생성 카드 필수, 직접 추가는 선택)',
  `source_timestamp_sec` INT NULL COMMENT '영상에서 장소가 나오는 시점(초)',
  `price_text` VARCHAR(50) NULL COMMENT '가격 표시 문구 (¥960~980, 1,078엔부터 등)',
  `review_count` INT NULL COMMENT '리뷰 수 (Google Maps 기준)',
  `opening_hours` VARCHAR(100) NULL COMMENT '영업시간 (11:00-24:00 등)',
  `access_text` VARCHAR(255) NULL COMMENT '찾아가는 길 (하카타역 동쪽 도보 6~7분 등)',
  `memo` VARCHAR(500) NULL COMMENT '메모',
  `sub_type` VARCHAR(30) NULL COMMENT '교통·팁 세부유형 (교통패스, 환전 등)',
  `applicable_region` VARCHAR(100) NULL COMMENT '교통·팁 적용지역',
  `cost` VARCHAR(255) NULL COMMENT '교통·팁 비용',
  `benefit` TEXT NULL COMMENT '교통·팁 혜택',
  `method` TEXT NULL COMMENT '교통·팁 방법',
  `place_text` VARCHAR(255) NULL COMMENT '교통·팁 장소 (구매처 등)',
  `caution` TEXT NULL COMMENT '교통·팁 주의',
  `created_at` DATETIME NOT NULL COMMENT '생성 일시',
  `updated_at` DATETIME NOT NULL COMMENT '수정 일시',
  `deleted_at` DATETIME NULL COMMENT '휴지통으로 옮긴 일시 (NULL이면 보관함, #58)',
  PRIMARY KEY (`place_id`),
  INDEX `idx_place_user_category` (`user_id`, `category`),
  INDEX `idx_place_user_city` (`user_id`, `city`),
  CONSTRAINT `FK_user_TO_place_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FK_source_link_TO_place_1` FOREIGN KEY (`source_link_id`) REFERENCES `source_link` (`source_link_id`)
) COMMENT='요약카드';

CREATE TABLE `place_key_point` (
  `place_key_point_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '핵심 포인트 ID',
  `place_id` BIGINT NOT NULL COMMENT '요약카드 ID',
  `content` VARCHAR(100) NOT NULL COMMENT '대표메뉴, 특징, 볼거리 등',
  `order_index` INT NOT NULL COMMENT '표시 순서 (카드당 최대 3개)',
  PRIMARY KEY (`place_key_point_id`),
  CONSTRAINT `FK_place_TO_place_key_point_1` FOREIGN KEY (`place_id`) REFERENCES `place` (`place_id`)
) COMMENT='요약카드 핵심 포인트';

CREATE TABLE `plan` (
  `plan_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '여행계획 ID',
  `user_id` BIGINT NOT NULL COMMENT '만든 회원 ID',
  `name` VARCHAR(50) NOT NULL COMMENT '여행명',
  `start_date` DATE NOT NULL COMMENT '시작일',
  `end_date` DATE NOT NULL COMMENT '종료일',
  `city` VARCHAR(100) NOT NULL COMMENT '여행 장소 (도시)',
  `city_place_id` VARCHAR(255) NULL COMMENT '도시 Google Maps 장소 ID',
  `latitude` DECIMAL(10,7) NULL COMMENT '도시 위도',
  `longitude` DECIMAL(10,7) NULL COMMENT '도시 경도',
  `status` VARCHAR(20) NOT NULL COMMENT 'DRAFT, COMPLETED',
  `share_type` VARCHAR(20) NULL COMMENT 'READ_ONLY, EDIT (NULL이면 공유 꺼짐)',
  `created_at` DATETIME NOT NULL COMMENT '생성 일시',
  `updated_at` DATETIME NOT NULL COMMENT '수정 일시',
  PRIMARY KEY (`plan_id`),
  CONSTRAINT `FK_user_TO_plan_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='여행계획';

CREATE TABLE `plan_day` (
  `plan_day_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '일자 ID',
  `plan_id` BIGINT NOT NULL COMMENT '여행계획 ID',
  `day_number` INT NOT NULL COMMENT 'N일차',
  `date` DATE NOT NULL COMMENT '날짜',
  `is_completed` TINYINT(1) NOT NULL COMMENT '그 날 완성 여부',
  PRIMARY KEY (`plan_day_id`),
  CONSTRAINT `uk_plan_day_plan_day_number` UNIQUE (`plan_id`, `day_number`),
  CONSTRAINT `FK_plan_TO_plan_day_1` FOREIGN KEY (`plan_id`) REFERENCES `plan` (`plan_id`)
) COMMENT='여행 일자';

CREATE TABLE `plan_day_area` (
  `plan_day_area_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '구역 ID',
  `plan_day_id` BIGINT NOT NULL COMMENT '일자 ID',
  `name` VARCHAR(100) NOT NULL COMMENT '둘러볼 구역 이름',
  `order_index` INT NOT NULL COMMENT '입력 순서',
  `latitude` DECIMAL(10,7) NULL COMMENT '구역 중심 위도',
  `longitude` DECIMAL(10,7) NULL COMMENT '구역 중심 경도',
  PRIMARY KEY (`plan_day_area_id`),
  CONSTRAINT `FK_plan_day_TO_plan_day_area_1` FOREIGN KEY (`plan_day_id`) REFERENCES `plan_day` (`plan_day_id`)
) COMMENT='일자별 구역';

CREATE TABLE `plan_stay` (
  `plan_stay_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '숙소 선택 ID',
  `plan_id` BIGINT NOT NULL COMMENT '여행계획 ID',
  `place_id` BIGINT NOT NULL COMMENT '숙소 요약카드 ID',
  PRIMARY KEY (`plan_stay_id`),
  CONSTRAINT `uk_plan_stay_plan_place` UNIQUE (`plan_id`, `place_id`),
  CONSTRAINT `FK_plan_TO_plan_stay_1` FOREIGN KEY (`plan_id`) REFERENCES `plan` (`plan_id`),
  CONSTRAINT `FK_place_TO_plan_stay_1` FOREIGN KEY (`place_id`) REFERENCES `place` (`place_id`)
) COMMENT='계획 숙소';

CREATE TABLE `plan_node` (
  `plan_node_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '노드 ID',
  `plan_id` BIGINT NOT NULL COMMENT '여행계획 ID (한 계획에 같은 장소 한 번만)',
  `plan_day_id` BIGINT NOT NULL COMMENT '일자 ID',
  `place_id` BIGINT NOT NULL COMMENT '요약카드 ID',
  `order_index` INT NOT NULL COMMENT '그 날의 방문 순서',
  `created_at` DATETIME NOT NULL COMMENT '확정 일시',
  PRIMARY KEY (`plan_node_id`),
  CONSTRAINT `uk_plan_node_plan_place` UNIQUE (`plan_id`, `place_id`),
  INDEX `idx_plan_node_day_order` (`plan_day_id`, `order_index`),
  CONSTRAINT `FK_plan_TO_plan_node_1` FOREIGN KEY (`plan_id`) REFERENCES `plan` (`plan_id`),
  CONSTRAINT `FK_plan_day_TO_plan_node_1` FOREIGN KEY (`plan_day_id`) REFERENCES `plan_day` (`plan_day_id`),
  CONSTRAINT `FK_place_TO_plan_node_1` FOREIGN KEY (`place_id`) REFERENCES `place` (`place_id`)
) COMMENT='계획 노드';

CREATE TABLE `plan_alternative` (
  `plan_alternative_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '플랜B ID',
  `plan_node_id` BIGINT NOT NULL COMMENT '대신할 노드 ID',
  `place_id` BIGINT NOT NULL COMMENT '대신 갈 요약카드 ID',
  `created_at` DATETIME NOT NULL COMMENT '추가 일시',
  PRIMARY KEY (`plan_alternative_id`),
  CONSTRAINT `FK_plan_node_TO_plan_alternative_1` FOREIGN KEY (`plan_node_id`) REFERENCES `plan_node` (`plan_node_id`),
  CONSTRAINT `FK_place_TO_plan_alternative_1` FOREIGN KEY (`place_id`) REFERENCES `place` (`place_id`)
) COMMENT='플랜B';

CREATE TABLE `plan_member` (
  `plan_member_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '멤버 ID',
  `plan_id` BIGINT NOT NULL COMMENT '여행계획 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `role` VARCHAR(20) NOT NULL COMMENT 'OWNER, EDITOR, VIEWER',
  `created_at` DATETIME NOT NULL COMMENT '참여 일시',
  PRIMARY KEY (`plan_member_id`),
  CONSTRAINT `uk_plan_member_plan_user` UNIQUE (`plan_id`, `user_id`),
  CONSTRAINT `FK_plan_TO_plan_member_1` FOREIGN KEY (`plan_id`) REFERENCES `plan` (`plan_id`),
  CONSTRAINT `FK_user_TO_plan_member_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) COMMENT='계획 멤버';
