-- ============================================
-- reports.admin_reminder_count / admin_reminded_at: 승인 대기(PENDING) 제보 리마인드(AdminReportReminder) 추적.
--   admin_reminder_count: 이 제보가 리마인드 푸시에 들어간 횟수(0~2). 30분·2시간 기준으로 최대 2번.
--   admin_reminded_at   : 마지막으로 리마인드에 들어간 시각(UTC). 없으면 NULL.
--   스케줄러가 조건부 UPDATE(count = 0/1 일 때만 +1)로 선점하므로 서버 여러 대·재시작에도 같은 단계를 두 번 보내지 않는다.
-- Report.java 엔티티와 필드 단위 대조 완료(adminReminderCount: TINYINT NOT NULL, adminRemindedAt: DATETIME NULL).
--
-- 선행: db/create_reports_table.sql, db/alter_admin_console.sql(reviewed_at 뒤에 붙임)
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 RDS에 직접 실행해야 함 (빠뜨리면 서버가 기동하지 않음).
-- 기존 행은 0 / NULL 로 채워진다 — 배포 직후 첫 회차에 이미 30분 넘게 대기 중인 제보들이 한 번에 요약돼 1회 나갈 수 있다(정상).
-- 한 번만 실행한다(두 번째 실행은 Duplicate column 오류 — 무해).
-- ============================================

ALTER TABLE `reports`
    ADD COLUMN `admin_reminder_count` TINYINT NOT NULL DEFAULT 0 AFTER `reviewed_at`,
    ADD COLUMN `admin_reminded_at` DATETIME NULL AFTER `admin_reminder_count`;

-- 확인용
-- SHOW COLUMNS FROM reports LIKE 'admin_remind%';
