-- 기존 Source 엔티티의 인덱스와 수동 SQL을 맞춰요. 데이터·컬럼은 바꾸지 않아요.
-- SHOW INDEX FROM source로 idx_source_user_created가 없는지 확인한 뒤 한 번만 적용해요.
CREATE INDEX `idx_source_user_created` ON `source` (`user_id`, `created_at`);
