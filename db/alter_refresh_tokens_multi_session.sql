-- ============================================
-- refresh_tokens 다중 세션 (2026-10-04, fix/multi-session-refresh). MySQL 8.
--   유저당 1 row(uq_refresh_token_user) → 세션(로그인 1번)당 1 row. 다른 기기·앱+웹·관리자 콘솔 로그인,
--   웹 여러 탭 동시 재발급, 재발급 응답 유실 재시도 때 다른 쪽이 401("로그인이 만료됐어요")로 튕기던 문제.
--   + previous_token_hash / rotated_at: 직전 토큰을 유예 시간(기본 60초) 동안만 알아보기 위한 컬럼.
--   코드: RefreshToken, RefreshTokenRepository, RefreshTokenService, RefreshTokenCleanup
--
-- ■ 순서: 이 스크립트 전체 실행(마지막 확인 SELECT) → PR 머지 → 배포
--   ddl-auto=validate 라 컬럼이 없으면 새 서버가 뜨지 않는다. 옛 서버는 새 컬럼을 모르지만(NULL 허용) 그대로 동작하므로
--   SQL 을 먼저 돌려도 안전하다. 단, SQL 실행 ~ 배포 사이 옛 서버는 여전히 유저당 1 row 로 동작(findByUser_Id 는
--   row 가 하나뿐이라 문제없음 — 옛 서버는 row 를 늘리지 않는다).
--   기존 row 는 그대로 유효: token_hash 가 지금 쓰는 refresh 토큰의 해시라 배포 후 첫 재발급에서 정상 로테이션된다.
--
-- ■ 처음부터 끝까지 다시 실행해도 안전하다(인덱스·컬럼은 없을 때만 만들고, 유니크는 있을 때만 지움).
--
-- ■ 순서 주의: user_id 에는 FK(fk_refresh_token_user)가 걸려 있어 인덱스가 하나는 있어야 한다.
--   일반 인덱스 idx_refresh_token_user 를 먼저 만들고 나서 유니크 uq_refresh_token_user 를 지운다
--   (거꾸로 하면 "needed in a foreign key constraint" 오류).
-- ============================================

-- 1) user_id 일반 인덱스 — 없을 때만
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND INDEX_NAME = 'idx_refresh_token_user');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `refresh_tokens` ADD INDEX `idx_refresh_token_user` (`user_id`)',
  'SELECT ''idx_refresh_token_user 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 유저당 1개 유니크 제거 — 있을 때만
SET @has_uq := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND INDEX_NAME = 'uq_refresh_token_user');
SET @ddl := IF(@has_uq > 0,
  'ALTER TABLE `refresh_tokens` DROP INDEX `uq_refresh_token_user`',
  'SELECT ''uq_refresh_token_user 이미 없음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) previous_token_hash (NULL 허용) — 없을 때만
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND COLUMN_NAME = 'previous_token_hash');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `refresh_tokens` ADD COLUMN `previous_token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NULL AFTER `token_hash`',
  'SELECT ''previous_token_hash 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) rotated_at (NULL 허용) — 없을 때만
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND COLUMN_NAME = 'rotated_at');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `refresh_tokens` ADD COLUMN `rotated_at` datetime NULL AFTER `previous_token_hash`',
  'SELECT ''rotated_at 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) token_hash 유니크(재발급·로그아웃이 해시로 찾음). 기존 row 는 유저당 1개이고 토큰에 유저 id(sub)가 들어 있어 겹치지 않는다.
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND INDEX_NAME = 'uq_refresh_token_hash');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `refresh_tokens` ADD UNIQUE INDEX `uq_refresh_token_hash` (`token_hash`)',
  'SELECT ''uq_refresh_token_hash 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) previous_token_hash 인덱스(유예 재사용 조회)
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND INDEX_NAME = 'idx_refresh_token_prev_hash');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `refresh_tokens` ADD INDEX `idx_refresh_token_prev_hash` (`previous_token_hash`)',
  'SELECT ''idx_refresh_token_prev_hash 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7) 확인 — 인덱스는 PRIMARY · idx_refresh_token_prev_hash · idx_refresh_token_user · uq_refresh_token_hash 4개
--    (uq_refresh_token_user 는 없어야 함), 컬럼은 2개 모두 IS_NULLABLE=YES
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' ORDER BY INDEX_NAME;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND COLUMN_NAME IN ('previous_token_hash', 'rotated_at');

-- 되돌리기(서버를 이전 버전으로 돌린 뒤 실행). 이전 서버는 findByUser_Id 가 row 1개라고 가정하므로, 유저마다 가장 최근
-- row 하나만 남기고 지운 다음 유니크를 다시 건다(지워진 세션은 다음 재발급 때 401 → 재로그인).
-- DELETE r FROM `refresh_tokens` r JOIN `refresh_tokens` n ON n.user_id = r.user_id
--   AND (n.updated_at > r.updated_at OR (n.updated_at = r.updated_at AND n.id > r.id));
-- ALTER TABLE `refresh_tokens` ADD UNIQUE INDEX `uq_refresh_token_user` (`user_id`);
-- ALTER TABLE `refresh_tokens` DROP INDEX `uq_refresh_token_hash`, DROP INDEX `idx_refresh_token_prev_hash`,
--   DROP COLUMN `rotated_at`, DROP COLUMN `previous_token_hash`;
-- (idx_refresh_token_user 는 남겨 둬도 무방)
