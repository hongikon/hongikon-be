-- ============================================
-- reports.content: 본문 최대 500 → 2000자. 학생회 행사 공지처럼 긴 안내를 그대로 옮길 수 있게. Report.java content 와 맞춤.
-- 길이만 늘리므로 기존 데이터는 그대로다. 배포 전에 실행해도, 다시 실행해도 안전(이미 2000 이상이면 건너뜀).
-- (ddl-auto=validate 는 varchar 길이를 검사하지 않아 순서가 바뀌어도 서버는 뜬다. 다만 실행 전엔 500자 넘는 본문 저장이 DB 에서 실패한다.)
-- ============================================
SET @len := (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND COLUMN_NAME = 'content');
SET @ddl := IF(@len IS NOT NULL AND @len < 2000,
  'ALTER TABLE `reports` MODIFY COLUMN `content` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL',
  'SELECT ''reports.content 이미 2000자 이상'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
