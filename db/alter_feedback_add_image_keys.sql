-- ============================================
-- feedback.image_keys: 문의 참고 사진 S3 키(최대 3장, 쉼표로 이음). Feedback.java imageKeys 와 맞춤.
-- ddl-auto=validate 라 배포 전에 RDS 에서 먼저 실행한다. 옛 서버는 이 컬럼을 모르므로 먼저 실행해도 안전, 다시 실행해도 안전.
-- 처리 완료(RESOLVED)하거나 작성자가 탈퇴하면 서버가 사진과 이 값을 지운다.
-- ============================================
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'image_keys');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `feedback` ADD COLUMN `image_keys` varchar(600) COLLATE utf8mb4_unicode_ci NULL',
  'SELECT ''image_keys 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
