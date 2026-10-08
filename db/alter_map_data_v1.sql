-- ============================================
-- 지도 데이터 서버 이전 v1 (2026-10-06). 앱(hongikon-fe) 상수로 그리던 제휴업체·편의시설·건물을
-- GET /map/data 로 내려주고 관리자 화면(/admin/map/**)에서 고칠 수 있게 하는 스키마 변경.
--   1) buildings  — display_name(앱 표시 이름 '홍문관 R동'. name 은 뉴스 장소 매칭용 짧은 이름이라 그대로 둔다),
--                   extra_boundaries(JSON), entrances(JSON), sort_order
--   2) partners   — code(앱 slug, 예: cafe-sunny-house) + UNIQUE, sort_order
--   3) campus_facilities 새 테이블 — 건물 안 편의시설(프린터·열람실 등)
--   코드: Building, Partner, mapdata.CampusFacility
--
-- ■ 순서: RDS 스냅샷 → 이 스크립트 → db/sync_map_data_2026_10_06.sql(데이터 채우기) → PR 머지 → 배포
--   ddl-auto=validate 라 이 SQL 보다 앱을 먼저 배포하면 서버가 뜨지 않는다.
--   새 컬럼은 모두 NULL 허용 또는 DEFAULT 가 있어 이전 버전 서버도 그대로 동작한다(먼저 실행해도 안전).
--
-- ■ 처음부터 끝까지 여러 번 실행해도 안전하다(컬럼·인덱스·테이블은 없을 때만 만든다).
-- ============================================

SET NAMES utf8mb4;

-- 1) buildings.display_name
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buildings' AND COLUMN_NAME = 'display_name');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `buildings` ADD COLUMN `display_name` varchar(100) COLLATE utf8mb4_unicode_ci NULL',
  'SELECT ''buildings.display_name 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) buildings.extra_boundaries — 떨어진 동의 추가 외곽선 [[[lat,lng],...],...]
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buildings' AND COLUMN_NAME = 'extra_boundaries');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `buildings` ADD COLUMN `extra_boundaries` json NULL',
  'SELECT ''buildings.extra_boundaries 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) buildings.entrances — [{label,lat,lng,minFloor,maxFloor}]
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buildings' AND COLUMN_NAME = 'entrances');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `buildings` ADD COLUMN `entrances` json NULL',
  'SELECT ''buildings.entrances 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) buildings.sort_order
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buildings' AND COLUMN_NAME = 'sort_order');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `buildings` ADD COLUMN `sort_order` int NOT NULL DEFAULT 0',
  'SELECT ''buildings.sort_order 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) partners.code
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partners' AND COLUMN_NAME = 'code');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `partners` ADD COLUMN `code` varchar(100) COLLATE utf8mb4_unicode_ci NULL',
  'SELECT ''partners.code 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) partners.code UNIQUE (NULL 은 여러 개 허용 — 동기화 전 기존 행은 NULL 로 남는다)
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partners' AND INDEX_NAME = 'uq_partners_code');
SET @ddl := IF(@has_idx = 0,
  'ALTER TABLE `partners` ADD UNIQUE INDEX `uq_partners_code` (`code`)',
  'SELECT ''uq_partners_code 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7) partners.sort_order
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partners' AND COLUMN_NAME = 'sort_order');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `partners` ADD COLUMN `sort_order` int NOT NULL DEFAULT 0',
  'SELECT ''partners.sort_order 이미 있음'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8) campus_facilities — 건물이 지워지지 않도록 ON DELETE RESTRICT(건물 행은 UPDATE 만 한다)
CREATE TABLE IF NOT EXISTS `campus_facilities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `building_id` bigint NOT NULL,
  `floor` int NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci NULL,
  `latitude` decimal(10,7) NULL,
  `longitude` decimal(10,7) NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_campus_facilities_code` (`code`),
  KEY `idx_campus_facilities_building` (`building_id`),
  CONSTRAINT `fk_campus_facilities_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buildings'
     AND COLUMN_NAME IN ('display_name', 'extra_boundaries', 'entrances', 'sort_order')) AS `buildings_new_cols_4`,
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partners'
     AND COLUMN_NAME IN ('code', 'sort_order')) AS `partners_new_cols_2`,
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_facilities') AS `facilities_table_1`;

-- 되돌리기(서버를 이전 버전으로 돌린 뒤. 이전 서버는 새 컬럼을 모르지만 NULL/DEFAULT 라 남겨 둬도 동작한다):
-- DROP TABLE `campus_facilities`;
-- ALTER TABLE `partners` DROP INDEX `uq_partners_code`, DROP COLUMN `code`, DROP COLUMN `sort_order`;
-- ALTER TABLE `buildings` DROP COLUMN `display_name`, DROP COLUMN `extra_boundaries`, DROP COLUMN `entrances`, DROP COLUMN `sort_order`;
