-- ============================================
-- 여러 날 제보의 날짜별 운영 시간(부스·간식행사 등, 날마다 다를 수 있음). (2026-11-08)
--   reports.schedule_days — "2026-11-14 14:00-18:00,2026-11-15 13:00-17:00"(한국 시간, 쉬는 날은 없음, 최대 8일).
--                           Report.java, ReportDaysConverter.java
-- NULL 이면 지금과 같은 한 번짜리 제보다. 지도 노출(starts_at~ends_at)은 바뀌지 않는다.
--
-- ddl-auto=validate 환경이므로 이 PR 을 배포하기 전에 운영 DB 에서 먼저 실행해야 한다.
-- 기존 제보는 모두 NULL(한 번짜리)로 남는다. 되돌릴 때는 아래 주석의 DROP 을 실행한다(서버를 이전 버전으로 돌린 뒤).
-- ============================================

ALTER TABLE `reports`
  ADD COLUMN `schedule_days` varchar(255) COLLATE utf8mb4_unicode_ci NULL;

-- 되돌리기:
-- ALTER TABLE `reports` DROP COLUMN `schedule_days`;
