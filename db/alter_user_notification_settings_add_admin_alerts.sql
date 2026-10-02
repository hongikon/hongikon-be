-- ============================================
-- user_notification_settings.admin_alerts_enabled: 관리자 알림(새 제보 승인 대기·새 문의·신고 누적 자동 숨김) 켜기/끄기.
--   role=ADMIN 유저에게만 의미가 있다. 기본 켜짐 — 행이 없는 관리자도 켜진 것으로 본다(AdminAlertDispatcher).
--   API: PATCH /users/me/notification-settings {"adminAlerts": false}
-- UserNotificationSetting.java 엔티티와 필드 단위 대조 완료.
--
-- 선행: db/create_user_notification_settings.sql (PR #6)
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 RDS에 직접 실행해야 함 (빠뜨리면 서버가 기동하지 않음).
-- NOT NULL DEFAULT TRUE라 기존 행은 모두 켜짐으로 채워진다. 한 번만 실행한다(두 번째 실행은 Duplicate column 오류 — 무해).
-- ============================================

ALTER TABLE `user_notification_settings`
    ADD COLUMN `admin_alerts_enabled` boolean NOT NULL DEFAULT TRUE AFTER `new_reports_scope`;

-- 확인용
-- SHOW COLUMNS FROM user_notification_settings LIKE 'admin_alerts_enabled';
