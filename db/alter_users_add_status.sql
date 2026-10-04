-- ============================================
-- 회원 이용 정지(약관 제10조, App Store 가이드라인 1.2). (2026-10-02)
--   users.status            — ACTIVE / SUSPENDED. User.java, UserStatus.java
--   users.suspended_reason  — 정지 사유(관리자 기록)
--   users.suspended_at      — 정지 시각
--
-- ddl-auto=validate 환경이므로 이 PR 을 배포하기 전에 운영 DB 에서 먼저 실행해야 한다.
-- 기존 회원은 모두 ACTIVE 로 채워진다. 되돌릴 때는 아래 주석의 DROP 을 실행한다(서버를 이전 버전으로 돌린 뒤).
-- ============================================

ALTER TABLE `users`
  ADD COLUMN `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN `suspended_reason` varchar(200) COLLATE utf8mb4_unicode_ci NULL,
  ADD COLUMN `suspended_at` datetime NULL;

-- 되돌리기:
-- ALTER TABLE `users` DROP COLUMN `status`, DROP COLUMN `suspended_reason`, DROP COLUMN `suspended_at`;
