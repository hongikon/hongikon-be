-- ============================================
-- 경로망(길찾기용 점·간선) 서버 이전 (2026-10-08). 앱(hongikon-fe) src/constants/pathNodes.ts 에 하드코딩된
-- 경로망을 GET /map/data 의 paths 로 내려주고 관리자 API(/admin/map/path-*)로 고칠 수 있게 하는 스키마.
--   1) path_nodes — 점. kind = WAYPOINT(경로 중간점, 좌표 저장) / ENTRANCE(건물 출입구 참조)
--                   ENTRANCE 는 (building_id, entrance_label) 로 buildings.entrances(JSON)의 출입구를 가리킨다.
--                   좌표는 복사하지 않고 응답을 만들 때 buildings.entrances 에서 읽는다(두 곳이 어긋나지 않게).
--                   entrances 가 JSON 이라 라벨에는 외래키를 걸 수 없다 — 깨진 참조는 GET /admin/map/path-audit 와
--                   아래 '확인' 쿼리로 찾는다.
--   2) path_edges — 양방향 간선 한 줄. 항상 node_a_id < node_b_id 로 저장한다(같은 간선 중복·자기 자신 연결 금지).
--   코드: mapdata.PathNode, mapdata.PathEdge
--   기존 route_nodes / route_edges(POST /routes/search, 건물+층 접속점 모델)와는 별개이고 그대로 둔다.
--
-- ■ 순서: RDS 스냅샷 → 이 스크립트 → 서버 배포(Deploy 워크플로 sql_applied=true) → 관리자 임포트
--   ddl-auto=validate 라 이 SQL 보다 앱을 먼저 배포하면 서버가 뜨지 않는다.
--   새 테이블만 더하므로 이전 버전 서버는 영향이 없다(먼저 실행해도 안전).
--
-- ■ 처음부터 끝까지 여러 번 실행해도 안전하다(테이블은 없을 때만 만든다).
-- ■ CHECK 제약은 MySQL 8.0.16+ 에서 검사한다(운영 8.4).
-- ============================================

SET NAMES utf8mb4;

-- 1) path_nodes — 건물이 지워지지 않도록 ON DELETE RESTRICT(기본값)
CREATE TABLE IF NOT EXISTS `path_nodes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `latitude` decimal(10,7) NULL,
  `longitude` decimal(10,7) NULL,
  `building_id` bigint NULL,
  `entrance_label` varchar(50) COLLATE utf8mb4_unicode_ci NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_path_nodes_code` (`code`),
  -- 출입구 하나에 노드 하나. WAYPOINT 는 둘 다 NULL 이라 여러 개여도 된다.
  UNIQUE KEY `uq_path_nodes_entrance` (`building_id`, `entrance_label`),
  CONSTRAINT `fk_path_nodes_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`),
  CONSTRAINT `ck_path_nodes_kind` CHECK (
    (`kind` = 'WAYPOINT' AND `latitude` IS NOT NULL AND `longitude` IS NOT NULL
       AND `building_id` IS NULL AND `entrance_label` IS NULL)
    OR (`kind` = 'ENTRANCE' AND `building_id` IS NOT NULL AND `entrance_label` IS NOT NULL
       AND `latitude` IS NULL AND `longitude` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) path_edges — 이어진 간선이 있으면 노드를 지울 수 없다(ON DELETE RESTRICT). (a, b) 유니크 인덱스가 a 쪽 조회를 맡는다.
CREATE TABLE IF NOT EXISTS `path_edges` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `node_a_id` bigint NOT NULL,
  `node_b_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_path_edges_pair` (`node_a_id`, `node_b_id`),
  KEY `idx_path_edges_node_b` (`node_b_id`),
  CONSTRAINT `fk_path_edges_node_a` FOREIGN KEY (`node_a_id`) REFERENCES `path_nodes` (`id`),
  CONSTRAINT `fk_path_edges_node_b` FOREIGN KEY (`node_b_id`) REFERENCES `path_nodes` (`id`),
  CONSTRAINT `ck_path_edges_order` CHECK (`node_a_id` < `node_b_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME IN ('path_nodes', 'path_edges')) AS `path_tables_2`,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
     AND CONSTRAINT_NAME IN ('ck_path_nodes_kind', 'ck_path_edges_order')) AS `check_constraints_2`;

-- 깨진 출입구 참조 확인(임포트 뒤, 그리고 db/sync_map_data_*.sql 로 buildings.entrances 를 덮어쓴 뒤 COMMIT 전에).
-- 결과가 있으면 그 라벨을 경로가 쓰고 있다 — 동기화 SQL 이면 ROLLBACK 하고 라벨을 되돌리거나 경로를 먼저 고친다.
-- SELECT pn.code, b.code AS building, pn.entrance_label
-- FROM path_nodes pn JOIN buildings b ON b.id = pn.building_id
-- WHERE pn.kind = 'ENTRANCE' AND NOT EXISTS (
--   SELECT 1 FROM JSON_TABLE(COALESCE(b.entrances, JSON_ARRAY()), '$[*]'
--          COLUMNS (label varchar(50) PATH '$.label')) j
--   WHERE j.label = pn.entrance_label);

-- 되돌리기(서버를 이전 버전으로 돌린 뒤. 이전 서버는 이 테이블을 모르므로 남겨 둬도 동작한다):
-- DROP TABLE `path_edges`;
-- DROP TABLE `path_nodes`;
