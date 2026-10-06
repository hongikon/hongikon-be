-- ============================================
-- reports.place_label: 장소 설명(최대 60자). 앱이 지도 핀 근처 건물로 채워 주고 작성자가 고칠 수 있다. Report.java placeLabel 과 맞춤.
-- ddl-auto=validate 라 배포 전에 RDS 에서 먼저 실행한다. 옛 서버는 이 컬럼을 모르므로 먼저 실행해도 안전, 다시 실행해도 안전.
-- ============================================
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND COLUMN_NAME = 'place_label');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `reports` ADD COLUMN `place_label` varchar(60) COLLATE utf8mb4_unicode_ci NULL AFTER `custom_category_label`',
  'SELECT ''place_label 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
