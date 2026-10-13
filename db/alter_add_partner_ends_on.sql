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

-- 소속별 종료일: 같은 업체가 여러 소속과 제휴했을 때 한 소속 제휴만 끝나는 경우(예: 미술대학 제휴만 12/31 까지).
-- 그 날이 지나면 그 소속만 지도에서 빠지고, 남은 소속이 하나도 없으면 업체도 빠진다. NULL 이면 기한 없음.
ALTER TABLE partner_affiliations
    ADD COLUMN ends_on DATE NULL AFTER benefit;

-- 확인용
-- SELECT code, name, ends_on FROM partners WHERE ends_on IS NOT NULL;
-- SELECT p.name, a.affiliation, a.ends_on FROM partner_affiliations a JOIN partners p ON p.id = a.partner_id WHERE a.ends_on IS NOT NULL;
