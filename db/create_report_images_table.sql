-- ============================================
-- report_images: 제보에 붙은 사진(제보당 최대 3장). S3 객체 키만 저장한다(예: reports/2f1c...-uuid.jpg).
-- URL 이 아니라 키만 저장한다 — 버킷은 비공개이고, 응답할 때마다 presigned GET URL 을 새로 만든다.
-- ReportImage.java 엔티티와 필드 단위 대조 검증 완료.
-- ddl-auto=validate 환경이므로 이 PR(#9)을 배포하기 전에 RDS 에 먼저 실행해야 함.
-- 이전 코드는 이 테이블을 모르므로 먼저 실행해도 안전하다. reports 테이블이 먼저 있어야 함.
-- 다시 실행해도 안전(IF NOT EXISTS).
-- ============================================

CREATE TABLE IF NOT EXISTS `report_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `image_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_images_key` (`image_key`),
  KEY `ix_report_images_report` (`report_id`,`sort_order`),
  CONSTRAINT `fk_report_images_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 이 PR 의 이전 버전(사진 1장) SQL `ALTER TABLE reports ADD COLUMN image_key ...` 를 이미 실행했다면
-- 그 컬럼은 이제 쓰지 않는다(엔티티에 없는 컬럼은 validate 가 무시하므로 남겨 둬도 동작함).
-- 정리하려면 이 PR 배포 후에 실행:
-- ALTER TABLE reports DROP COLUMN image_key;
