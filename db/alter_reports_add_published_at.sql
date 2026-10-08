-- ============================================
-- reports.published_at: 새 제보 알림 모아 보내기 (feat/new-report-digest, 2026-10-06)
--
-- 제보가 "새 제보"로 처음 지도에 뜬 시각(UTC). 새 제보 알림 다이제스트(NewReportDigestScheduler)가
-- "유저가 마지막으로 새 제보 알림을 받은 뒤 새로 뜬 제보"를 고를 때 쓴다.
--   - 승인 대기(PENDING) → 승인(ACTIVE) 때, 이미 시작한 제보면 승인 시각으로 채운다(AdminReportService.moderate).
--   - 시작 전에 승인된 예정 제보는 시작 알림을 보내는 순간(ReportStartPushScheduler) 채운다 — 조건부 UPDATE 로 선점해
--     서버가 여러 대여도 한 번만 보낸다.
--   - 숨김 해제(HIDDEN → ACTIVE)·재승인(ACTIVE → ACTIVE)은 reviewed_at 만 바뀌고 이 값은 그대로 — "새" 제보로 다시 묶이지 않는다.
--     (reviewed_at 은 매 검토마다 바뀌어 다이제스트 기준으로 쓸 수 없다.)
-- 기존 행은 NULL 로 남는다(이미 알림이 나간 제보라 다이제스트 대상이 아님 — 백필 불필요).
-- Report.java 엔티티와 필드 단위 대조 완료(publishedAt: DATETIME(6) NULL).
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 RDS에 직접 실행해야 함 (빠뜨리면 서버가 기동하지 않음).
-- 여러 번 실행해도 안전함 (컬럼·인덱스가 없을 때만 추가).
-- ============================================

-- 1) published_at (NULL 허용) — 없을 때만
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND COLUMN_NAME = 'published_at');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `reports` ADD COLUMN `published_at` datetime(6) NULL AFTER `reviewed_at`',
  'SELECT ''published_at 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 다이제스트 후보 조회(status = ACTIVE AND published_at 구간)용 인덱스 — 없을 때만
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND INDEX_NAME = 'ix_reports_status_published');
SET @ddl := IF(@has_idx = 0,
  'CREATE INDEX `ix_reports_status_published` ON `reports` (`status`, `published_at`)',
  'SELECT ''ix_reports_status_published 인덱스 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 확인용
-- SHOW COLUMNS FROM reports LIKE 'published_at';
