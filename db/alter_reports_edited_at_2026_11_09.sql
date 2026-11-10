-- ============================================
-- reports.edited_at: 작성자가 제보를 고친 시각 — 2026-11-09 (feat/report-owner-edit)
-- 작성자가 지도 카드 ⋯ 에서 자기 제보를 고치면(PUT /reports/{id}) 바로 반영되고, 앱·관리자 화면에 '수정됨'으로 보인다.
-- 코드: report.Report.editedAt.
-- ■ ddl-auto=validate 라 배포 전에 실행해야 한다. 두 번 실행하면 '중복 열' 오류가 나니 SHOW COLUMNS 로 먼저 확인한다.
-- ============================================

ALTER TABLE `reports` ADD COLUMN `edited_at` datetime(6) NULL AFTER `updated_at`;

-- 확인: SHOW COLUMNS FROM reports LIKE 'edited_at';
