-- ============================================
-- user_notification_settings: 유저별 제보 알림 설정 (게시판·카테고리·키워드 외의 알림).
--   report_status_enabled   내 제보 승인·반려 알림 (기본 켜짐)
--   new_reports_enabled     캠퍼스 새 제보 알림 (기본 꺼짐 — 스팸 방지)
--   new_reports_scope       새 제보 알림 범위. 지금은 CAMPUS뿐 (나중에 관심 건물 등으로 확장할 자리)
--   new_report_last_sent_at 새 제보 푸시를 마지막으로 보낸 시각 — push.report-new-throttle-minutes(기본 30분) 빈도 제한용
-- 행이 없으면 기본값(결과 알림 켜짐, 새 제보 알림 꺼짐)으로 본다. 처음 설정을 바꿀 때 행이 생긴다.
-- UserNotificationSetting.java 엔티티와 필드 단위 대조 완료.
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 RDS에 직접 실행해야 함 (빠뜨리면 서버가 기동하지 않음).
-- user_board_subscriptions(db/create_user_board_subscriptions.sql, PR #5)를 먼저 실행한 뒤 실행한다.
-- 여러 번 실행해도 안전함 (CREATE TABLE IF NOT EXISTS).
-- ============================================

CREATE TABLE IF NOT EXISTS `user_notification_settings` (
  `user_id` bigint NOT NULL,
  `report_status_enabled` boolean NOT NULL DEFAULT TRUE,
  `new_reports_enabled` boolean NOT NULL DEFAULT FALSE,
  `new_reports_scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CAMPUS',
  `new_report_last_sent_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  KEY `ix_notif_setting_new_reports` (`new_reports_enabled`,`new_report_last_sent_at`),
  CONSTRAINT `fk_notif_setting_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인용
-- SELECT COUNT(*), SUM(new_reports_enabled), SUM(NOT report_status_enabled) FROM user_notification_settings;
