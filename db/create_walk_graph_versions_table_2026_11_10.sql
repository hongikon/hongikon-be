-- ============================================
-- walk_graph_versions: 길찾기 경로망 버전 — 2026-11-10 (feat/route-graph-versions)
-- 경로 조사 도구(/dev/path-survey-v3)의 '서버에 올리기'가 초안(DRAFT)으로 저장하고, '운영에 적용'하면 ACTIVE 가 된다(이전 ACTIVE 는 ARCHIVED).
-- ACTIVE 가 없으면 서버는 저장소 파일(resources/walkroute/survey-graph-v3.json)을 쓴다. 코드: walkroute.WalkGraphVersion.
--
-- ■ graph = 서버 길찾기 경로망 JSON(정점·간선·메모), backup = 조사 도구 백업 원본(다시 불러오기용), report = 올릴 때 검사·비교 결과.
-- ■ 개인정보 없음(캠퍼스 길 자료).
-- ■ ddl-auto=validate 라 배포 전에 실행해야 한다. 여러 번 실행해도 안전하다(없을 때만 만든다).
-- ============================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `walk_graph_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(200) COLLATE utf8mb4_unicode_ci NULL,
  `exported_at` varchar(40) COLLATE utf8mb4_unicode_ci NULL,
  `vertex_count` int NOT NULL,
  `edge_count` int NOT NULL,
  `graph` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `backup` longtext COLLATE utf8mb4_unicode_ci NULL,
  `report` longtext COLLATE utf8mb4_unicode_ci NULL,
  `created_at` datetime(6) NOT NULL,
  `activated_at` datetime(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_walk_graph_versions_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인: SHOW CREATE TABLE walk_graph_versions;
