-- ============================================
-- 제보 키워드 알림 (feat/report-keywords, 2026-10-06)
--
-- report_keyword_subscriptions: 유저별 제보 전용 키워드. 소식 키워드(keyword_subscriptions)와 별개.
--   새 제보가 지도에 올라갈 때 제목·본문·장소 설명·직접 입력 분류·건물명에 키워드가 들어 있으면(대소문자·공백 무시)
--   새 제보 알림을 켠 유저에게 빈도 제한 없이 먼저 보낸다(ReportPushDispatcher). 유저당 최대 30개, 30자.
--   ReportKeywordSubscription.java 엔티티와 필드 단위 대조 완료.
--
-- report_keyword_push_log: 같은 제보로 같은 유저에게 키워드 알림이 두 번 가지 않게 하는 발송 기록(report_id, user_id 유일).
--   예정 제보 스케줄러가 같은 제보를 다시 집어도 중복되지 않고, 같은 제보의 일반 새 제보 알림에서도 이 유저를 뺀다.
--   키워드 내용은 남기지 않는다. 제보·유저가 지워지면 같이 지워진다(ON DELETE CASCADE).
--   ReportKeywordPushLog.java 엔티티와 필드 단위 대조 완료.
--
-- user_notification_settings.new_reports_scope 에 'KEYWORDS' 값이 새로 들어간다 — varchar(20) 그대로라 변경 없음.
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 RDS에 직접 실행해야 함 (빠뜨리면 서버가 기동하지 않음).
-- users, reports 테이블이 있어야 한다. 여러 번 실행해도 안전함 (CREATE TABLE IF NOT EXISTS).
-- ============================================

CREATE TABLE IF NOT EXISTS `report_keyword_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `keyword` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_keyword_user_keyword` (`user_id`,`keyword`),
  CONSTRAINT `fk_report_keyword_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_keyword_push_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_kw_push_report_user` (`report_id`,`user_id`),
  KEY `ix_report_kw_push_user` (`user_id`),
  CONSTRAINT `fk_report_kw_push_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_kw_push_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인용
-- SELECT COUNT(*), COUNT(DISTINCT user_id) FROM report_keyword_subscriptions;
-- SELECT COUNT(*) FROM report_keyword_push_log;
-- SELECT new_reports_scope, COUNT(*) FROM user_notification_settings GROUP BY new_reports_scope;
