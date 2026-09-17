-- ============================================
-- reports: 실시간 제보. 로그인 유저가 지도의 특정 지점(건물+층)에 올리는
-- 시간 한정 이벤트 정보. ends_at이 지나면 지도에서 자동으로 빠짐.
-- 확정 스펙(2026-09-08) 전부 반영된 최종 형태 — 신규 DB 생성 시 이 스크립트만 실행하면 됨.
-- (기존에 확정 스펙 이전 버전의 reports 테이블이 이미 있는 경우에는 이 파일 대신
--  db/alter_reports_table.sql을 사용할 것)
-- Report.java / ReportCategory.java / ReportStatus.java 엔티티와 필드 단위 대조 검증 완료.
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 DB에 직접 실행해야 함.
-- ============================================

CREATE TABLE `reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `building_id` bigint NOT NULL,
  `floor` int NOT NULL,
  `lat` decimal(10,7) NOT NULL,
  `lng` decimal(10,7) NOT NULL,
  `category` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `custom_category_label` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `starts_at` datetime NOT NULL,
  `ends_at` datetime NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_reports_live` (`status`,`ends_at`,`starts_at`),
  KEY `ix_reports_bld` (`building_id`,`floor`),
  KEY `ix_reports_user` (`user_id`,`created_at` DESC),
  CONSTRAINT `fk_reports_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `reports_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
