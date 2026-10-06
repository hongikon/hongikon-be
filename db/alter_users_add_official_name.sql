-- ============================================
-- users.official_name: 운영진이 인증한 공식 계정(학생회 등)의 이름. 있으면 앱 닉네임 대신 보이고 공식 배지가 붙는다.
-- User.java officialName 과 맞춤. ddl-auto=validate 라 이 PR 을 배포하기 전에 RDS 에서 먼저 실행한다.
-- 옛 서버는 이 컬럼을 모르므로 먼저 실행해도 안전. 다시 실행해도 안전(컬럼·인덱스가 있으면 건너뜀).
-- ============================================
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'official_name');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `users` ADD COLUMN `official_name` varchar(30) COLLATE utf8mb4_unicode_ci NULL',
  'SELECT ''official_name 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'uq_users_official_name');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `users` ADD UNIQUE INDEX `uq_users_official_name` (`official_name`)',
  'SELECT ''uq_users_official_name 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
