-- ============================================
-- partners.ends_on: 제휴가 끝나는 날(2026-10-13, 기간 한정 제휴 자동 만료).
--
-- 이 날까지 지도(GET /map/data)에 보이고, 다음 날 0시(한국 시간)부터 자동으로 빠진다.
-- NULL 이면 기한 없음 — 기존 행은 모두 NULL 로 시작해 지금과 똑같이 보인다.
-- 행은 지워지지 않고 관리자 목록(GET /admin/map/partners)에는 계속 남는다.
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 RDS에 직접 실행해야 함.
-- 이 스크립트를 실행하지 않고 새 엔티티만 배포하면 컬럼 불일치로 앱이 기동에 실패한다.
-- (순서: 1) 이 스크립트 실행 → 2) 새 백엔드 배포 → 3) db/sync_partners_2026_10_13.sql 실행)
-- ============================================

ALTER TABLE partners
    ADD COLUMN ends_on DATE NULL AFTER sort_order;

-- 확인용
-- SELECT code, name, ends_on FROM partners WHERE ends_on IS NOT NULL;
