-- #56: schema.sql로 이미 만든 DB에 BE 2 테이블의 유니크 제약·인덱스를 더해요. 빈 DB는 schema.sql만 적용하면 돼요.
-- prod ddl-auto=validate는 유니크·인덱스를 검사하지 않아서 빠져도 서버는 떠요. 빠지면 공유 계획 동시 열기 때 멤버가 중복돼 500이 나요.
-- 적용 전에 아래 SELECT로 중복 행이 없는지 먼저 확인해요. 결과가 있으면 데이터를 어떻게 정리할지 정한 뒤 적용해요(임의로 지우지 않아요).

SELECT plan_id, day_number, COUNT(*) FROM plan_day GROUP BY plan_id, day_number HAVING COUNT(*) > 1;
SELECT plan_id, place_id, COUNT(*) FROM plan_stay GROUP BY plan_id, place_id HAVING COUNT(*) > 1;
SELECT plan_id, place_id, COUNT(*) FROM plan_node GROUP BY plan_id, place_id HAVING COUNT(*) > 1;
SELECT plan_id, user_id, COUNT(*) FROM plan_member GROUP BY plan_id, user_id HAVING COUNT(*) > 1;

ALTER TABLE `plan_day` ADD CONSTRAINT `uk_plan_day_plan_day_number` UNIQUE (`plan_id`, `day_number`);
ALTER TABLE `plan_stay` ADD CONSTRAINT `uk_plan_stay_plan_place` UNIQUE (`plan_id`, `place_id`);
ALTER TABLE `plan_node` ADD CONSTRAINT `uk_plan_node_plan_place` UNIQUE (`plan_id`, `place_id`);
ALTER TABLE `plan_member` ADD CONSTRAINT `uk_plan_member_plan_user` UNIQUE (`plan_id`, `user_id`);

CREATE INDEX `idx_plan_node_day_order` ON `plan_node` (`plan_day_id`, `order_index`);
CREATE INDEX `idx_place_user_category` ON `place` (`user_id`, `category`);
CREATE INDEX `idx_place_user_city` ON `place` (`user_id`, `city`);
CREATE INDEX `idx_source_user_created` ON `source` (`user_id`, `created_at`);
