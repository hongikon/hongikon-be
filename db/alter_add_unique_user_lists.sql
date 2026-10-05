-- ============================================
-- 유저별 목록 테이블 유니크 키 보강 (2026-10-05 버그 점검)
--   bookmarks(user_id, news_id) · keyword_subscriptions(user_id, keyword) · user_departments(user_id, department_id)
--
-- 왜: 세 API 모두 "있는지 확인 → INSERT" 라 같은 요청이 동시에 두 번 오면(더블 탭·재시도) 중복 행이 생길 수 있다.
--   앱 코드는 이제 유니크 키 위반을 받아 새 트랜잭션에서 한 번 더 시도(UniqueConflictRetry)하거나 409 로 돌려주는데,
--   그러려면 DB 에 유니크 키가 있어야 한다.
--   - bookmarks: db/cleanup_arch_duplicate_news.sql 주석상 UNIQUE(user_id, news_id) 가 이미 있다.
--   - keyword_subscriptions: 엔티티 주석상 UNIQUE(user_id, keyword) 가 이미 있다.
--   - user_departments: 저장소에 생성 DDL 이 없어 있는지 확인되지 않았다.
--   그래서 셋 다 중복을 정리한 뒤 "같은 컬럼 조합의 유니크 인덱스가 없을 때만" 만든다. 이미 있으면 아무것도 바꾸지 않는다.
--
-- ■ 순서: RDS 스냅샷 → 이 스크립트 실행(맨 아래 확인 SELECT) → 배포. 배포 전후 어느 때 실행해도 앱은 뜬다
--   (ddl-auto=validate 는 유니크 키를 비교하지 않는다). 다만 실행 전까지는 동시 요청 시 중복 행이 생길 수 있다
--   (삭제 API 는 중복이 있어도 전부 지우도록 바꿔 두었다).
-- ■ 처음부터 끝까지 다시 실행해도 안전하다.
-- ■ 중복 정리 규칙: 같은 조합에서 id 가 가장 작은 행만 남긴다. user_departments 는 지우는 쪽이 주 학과였으면 남는 행을 주 학과로.
--   keyword_subscriptions.keyword 는 콜레이션(utf8mb4_unicode_ci)상 대소문자만 다른 값도 같은 값으로 본다(유니크 키와 같은 기준).
-- ============================================

-- 0) 실행 전 확인 — 이미 있는 유니크 인덱스(컬럼 조합)와 중복 건수
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
  AND TABLE_NAME IN ('bookmarks', 'keyword_subscriptions', 'user_departments')
GROUP BY TABLE_NAME, INDEX_NAME;

SELECT
  (SELECT COUNT(*) FROM (SELECT 1 FROM `bookmarks` GROUP BY `user_id`, `news_id` HAVING COUNT(*) > 1) d) AS dup_bookmarks,
  (SELECT COUNT(*) FROM (SELECT 1 FROM `keyword_subscriptions` GROUP BY `user_id`, `keyword` HAVING COUNT(*) > 1) d) AS dup_keywords,
  (SELECT COUNT(*) FROM (SELECT 1 FROM `user_departments` GROUP BY `user_id`, `department_id` HAVING COUNT(*) > 1) d) AS dup_departments;

-- 중복 정리는 유니크 키가 이미 있으면 지울 게 없어 그냥 돌려도 아무것도 안 바뀐다. 인덱스 추가만 "없을 때"로 건다.

-- ---------- 1) bookmarks(user_id, news_id) ----------
DELETE b FROM `bookmarks` b
  JOIN `bookmarks` k ON k.`user_id` = b.`user_id` AND k.`news_id` = b.`news_id` AND k.`id` < b.`id`;

SET @has := (SELECT COUNT(*) FROM (
               SELECT INDEX_NAME FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookmarks' AND NON_UNIQUE = 0
               GROUP BY INDEX_NAME
               HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'user_id,news_id') t);
SET @sql := IF(@has = 0,
  'ALTER TABLE `bookmarks` ADD UNIQUE INDEX `uq_bookmark_user_news` (`user_id`, `news_id`)',
  'SELECT ''bookmarks 유니크 이미 있음'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2) keyword_subscriptions(user_id, keyword) ----------
DELETE s FROM `keyword_subscriptions` s
  JOIN `keyword_subscriptions` k ON k.`user_id` = s.`user_id` AND k.`keyword` = s.`keyword` AND k.`id` < s.`id`;

SET @has := (SELECT COUNT(*) FROM (
               SELECT INDEX_NAME FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'keyword_subscriptions' AND NON_UNIQUE = 0
               GROUP BY INDEX_NAME
               HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'user_id,keyword') t);
SET @sql := IF(@has = 0,
  'ALTER TABLE `keyword_subscriptions` ADD UNIQUE INDEX `uq_keyword_user_keyword` (`user_id`, `keyword`)',
  'SELECT ''keyword_subscriptions 유니크 이미 있음'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3) user_departments(user_id, department_id) ----------
-- 남길 행(id 최소)과, 지울 중복 중 주 학과가 있었는지를 임시 테이블에 모은 뒤 반영한다
-- (같은 테이블을 UPDATE 하면서 서브쿼리로 읽는 MySQL 1093 오류를 피하려고).
DROP TEMPORARY TABLE IF EXISTS `tmp_ud_keep`;
CREATE TEMPORARY TABLE `tmp_ud_keep` AS
  SELECT `user_id`, `department_id`, MIN(`id`) AS `keep_id`, MAX(`is_primary`) AS `any_primary`
  FROM `user_departments`
  GROUP BY `user_id`, `department_id`
  HAVING COUNT(*) > 1;
UPDATE `user_departments` u JOIN `tmp_ud_keep` t ON u.`id` = t.`keep_id` SET u.`is_primary` = t.`any_primary`;
DELETE u FROM `user_departments` u
  JOIN `tmp_ud_keep` t ON u.`user_id` = t.`user_id` AND u.`department_id` = t.`department_id` AND u.`id` <> t.`keep_id`;
DROP TEMPORARY TABLE `tmp_ud_keep`;

SET @has := (SELECT COUNT(*) FROM (
               SELECT INDEX_NAME FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_departments' AND NON_UNIQUE = 0
               GROUP BY INDEX_NAME
               HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'user_id,department_id') t);
SET @sql := IF(@has = 0,
  'ALTER TABLE `user_departments` ADD UNIQUE INDEX `uq_user_department` (`user_id`, `department_id`)',
  'SELECT ''user_departments 유니크 이미 있음'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 확인 — 세 값 모두 0, 그리고 0) 의 첫 SELECT 를 다시 돌려 세 테이블에 유니크 인덱스가 하나씩 보여야 한다.
SELECT
  (SELECT COUNT(*) FROM (SELECT 1 FROM `bookmarks` GROUP BY `user_id`, `news_id` HAVING COUNT(*) > 1) d) AS dup_bookmarks,
  (SELECT COUNT(*) FROM (SELECT 1 FROM `keyword_subscriptions` GROUP BY `user_id`, `keyword` HAVING COUNT(*) > 1) d) AS dup_keywords,
  (SELECT COUNT(*) FROM (SELECT 1 FROM `user_departments` GROUP BY `user_id`, `department_id` HAVING COUNT(*) > 1) d) AS dup_departments;

-- 되돌리기(이 스크립트가 만든 인덱스만. 원래 있던 유니크 키는 이름이 달라 영향 없음):
-- ALTER TABLE `bookmarks` DROP INDEX `uq_bookmark_user_news`;
-- ALTER TABLE `keyword_subscriptions` DROP INDEX `uq_keyword_user_keyword`;
-- ALTER TABLE `user_departments` DROP INDEX `uq_user_department`;
