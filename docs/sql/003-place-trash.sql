-- #58 요약카드 휴지통: schema.sql로 이미 만든 DB에 휴지통 컬럼을 더해요. 빈 DB는 schema.sql만 적용하면 돼요.
-- 기존 카드는 모두 NULL(보관함)이라 데이터는 바뀌지 않아요. prod ddl-auto=validate라 이 컬럼이 없으면 새 버전 서버가 뜨지 않아요.
ALTER TABLE `place` ADD COLUMN `deleted_at` DATETIME NULL COMMENT '휴지통으로 옮긴 일시 (NULL이면 보관함, #58)' AFTER `updated_at`;
