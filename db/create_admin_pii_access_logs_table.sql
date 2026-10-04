-- ============================================
-- admin_pii_access_logs: 운영진의 회원 개인정보 열람 기록 (2026-10-05, 관리자 콘솔 로그인 닉네임 가리기)
--   - 관리자 응답은 이제 앱에 보이는 이름(앱 닉네임 또는 "홍**")과 회원 번호만 싣고, 로그인(카카오/Apple) 닉네임 원문은
--     GET /admin/users/{id}/login-name 으로만 볼 수 있다. 부를 때마다 한 줄씩 남는다(누가·언제·누구의·무엇을·사유).
--     열람한 값 자체는 남기지 않는다.
--   - users 와 FK 를 걸지 않는다(admin_user_id·target_user_id 둘 다). 탈퇴하면 users 행이 지워지지만 기록은 보관 기간 동안
--     남아야 하고(ON DELETE CASCADE 면 기록이 사라지고, SET NULL 이면 "누구의 것"이 사라진다), 탈퇴 뒤에는 숫자 id 만 남아
--     개인을 알아볼 수 없다. 그래서 UserService.withdraw 도 이 테이블은 정리하지 않는다.
--   - 보관: 안전성 확보조치 기준 제8조 — 1년 이상. 기본 2년(ADMIN_PII_ACCESS_LOG_RETENTION_DAYS=730),
--     AdminPiiAccessLogPurger 가 매일 04:30 지난 행을 지운다(설정을 줄여도 365일 미만으로는 지우지 않음).
--   - 코드: AdminPiiAccessLog, AdminPiiAccessService, AdminPiiAccessLogPurger
--
-- ■ ddl-auto=validate 이므로 서버 배포 전에 실행한다(테이블 없이 새 서버를 띄우면 뜨지 않는다).
--   다시 실행해도 안전하다(IF NOT EXISTS). 옛 서버는 이 테이블을 모르니 먼저 만들어 둬도 영향 없다.
-- ============================================

CREATE TABLE IF NOT EXISTS `admin_pii_access_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_user_id` bigint NOT NULL,
  `target_user_id` bigint NOT NULL,
  `field` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(100) COLLATE utf8mb4_unicode_ci NULL,
  `accessed_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_pii_access_logs_target` (`target_user_id`, `accessed_at`),
  KEY `idx_admin_pii_access_logs_admin` (`admin_user_id`, `accessed_at`),
  KEY `idx_admin_pii_access_logs_accessed` (`accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
-- SHOW CREATE TABLE admin_pii_access_logs;
-- 점검 예시(최근 30일 관리자별 열람 수):
-- SELECT admin_user_id, COUNT(*) FROM admin_pii_access_logs WHERE accessed_at >= NOW() - INTERVAL 30 DAY GROUP BY admin_user_id;

-- 되돌리기: 서버를 이전 버전으로 돌린 뒤 테이블을 지운다. 보관 기간 안의 기록이 사라지므로 먼저 백업(mysqldump)한다.
