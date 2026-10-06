-- ============================================
-- exhibitions: 장소(편의시설 kind '행사·전시')별 전시 일정 — 2026-10-06 (feat/exhibitions)
-- 앱 지도 '이벤트 → 전시' 에서 장소마다 지금·다음 전시를 보여 준다. GET /map/data 의 exhibitions, 편집은 /admin/map/exhibitions.
-- 코드: mapdata.Exhibition (필드 단위 대조 완료).
--
-- ■ facility_code 는 campus_facilities.code 를 가리키지만 외래키를 두지 않는다 —
--   지도 데이터 동기화 SQL(db/sync_map_data_*.sql)이 campus_facilities 를 지우고 다시 넣기 때문.
--   없는 장소의 전시는 GET /map/data 에서 빠질 뿐 지워지지 않는다.
-- ■ starts_on / ends_on 은 KST 달력 날짜(양 끝 포함).
-- ■ ddl-auto=validate 라 배포 전에 실행해야 한다. 여러 번 실행해도 안전하다(없을 때만 만든다).
-- ============================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `exhibitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `facility_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `starts_on` date NOT NULL,
  `ends_on` date NOT NULL,
  `hours` varchar(100) COLLATE utf8mb4_unicode_ci NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NULL,
  `link_label` varchar(50) COLLATE utf8mb4_unicode_ci NULL,
  `link_url` varchar(500) COLLATE utf8mb4_unicode_ci NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_exhibitions_facility_code` (`facility_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인: SHOW CREATE TABLE exhibitions;
