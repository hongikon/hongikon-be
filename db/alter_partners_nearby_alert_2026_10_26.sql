-- ============================================
-- 근처 제휴 알림(2026-10-26). 앱이 기기 안에서만 위치를 판단하고, 서버에는 위치를 보내지 않는다 — 위치 컬럼 없음.
--
-- 1) partners.nearby_alert: 근처 알림 대상 업체(관리자가 고른 곳만). Partner.nearbyAlert 와 맞춤.
--    NOT NULL DEFAULT FALSE 라 기존 행은 모두 꺼짐으로 채워진다. GET /map/data 의 partner 에 true 일 때만 nearbyAlert:true.
-- 2) user_notification_settings.partner_ad_consent_at: 제휴 혜택 알림(광고성 정보) 수신 동의 시각(UTC). NULL = 미동의.
--    정보통신망법 제50조 동의 기록·2년 재확인 안내용. UserNotificationSetting.partnerAdConsentAt 과 맞춤.
--    회원탈퇴 시 행째 지워진다(UserService + FK ON DELETE CASCADE).
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 RDS에 직접 실행해야 함 (빠뜨리면 새 서버가 기동하지 않음).
-- 컬럼만 더하므로 먼저 실행해도 지금 서버에는 영향이 없다. 다시 실행해도 안전(이미 있으면 건너뛴다).
-- 순서: 1) 이 스크립트 실행 → 2) 새 백엔드 배포. docs/deploy-runbook-nearby-alert.md
-- ============================================

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partners' AND COLUMN_NAME = 'nearby_alert');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `partners` ADD COLUMN `nearby_alert` boolean NOT NULL DEFAULT FALSE AFTER `usage_note`',
  'SELECT ''partners.nearby_alert 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification_settings' AND COLUMN_NAME = 'partner_ad_consent_at');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `user_notification_settings` ADD COLUMN `partner_ad_consent_at` datetime(6) NULL AFTER `new_report_last_sent_at`',
  'SELECT ''user_notification_settings.partner_ad_consent_at 컬럼 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 확인용
-- SHOW COLUMNS FROM partners LIKE 'nearby_alert';
-- SHOW COLUMNS FROM user_notification_settings LIKE 'partner_ad_consent_at';
-- SELECT COUNT(*) FROM partners WHERE nearby_alert;                                    -- 처음엔 0
-- SELECT COUNT(*) FROM user_notification_settings WHERE partner_ad_consent_at IS NOT NULL;  -- 처음엔 0
