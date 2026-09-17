-- ============================================
-- report_flags: 제보 신고. (report_id, user_id) 유니크 — 동일 유저의 중복 신고 방지.
-- ReportFlag.java 엔티티와 필드 단위 대조 검증 완료.
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 DB에 직접 실행해야 함.
-- reports 테이블이 먼저 생성되어 있어야 함 (db/create_reports_table.sql 선실행).
-- ============================================

CREATE TABLE `report_flags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `reason` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_flag` (`report_id`,`user_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `report_flags_ibfk_1` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE,
  CONSTRAINT `report_flags_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
