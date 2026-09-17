-- ============================================
-- buildings / places / route_nodes: 엔티티에는 있으나 hongikon-fe의 docs/schema.sql
-- (리뷰 반영본, 초기 버전)에는 반영되지 않은 컬럼 3개를 추가한다.
--
--   - buildings.code     : 건물 슬러그 (예: hongik_k)         — Building.java
--   - places.code        : 시설 슬러그 (예: hongik_k_studyroom) — Place.java
--   - route_nodes.point_no : 건물+층 내 접속점 식별자          — RouteNode.java
--   - route_nodes.code    : 건물+층 슬러그 (예: hongik_k_floor_1) — RouteNode.java
--
-- 전부 nullable(값이 점진적으로 채워지는 컬럼)이라 기존 행이 있어도 안전하게 추가 가능.
-- code 컬럼은 엔티티에 unique = true 로 선언되어 있어 UNIQUE 제약도 함께 추가한다
-- (ddl-auto=validate 자체는 unique 제약을 검증하지 않지만, 중복 삽입을 막기 위해 필요).
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 RDS에 직접 실행해야 함.
-- schema.sql 원본과 엔티티 클래스는 건드리지 않음 — RDS에만 적용하는 보정 스크립트.
-- ============================================

ALTER TABLE buildings
    ADD COLUMN code VARCHAR(100) NULL AFTER name;

ALTER TABLE buildings
    ADD CONSTRAINT uq_building_code UNIQUE (code);

ALTER TABLE places
    ADD COLUMN code VARCHAR(100) NULL AFTER name;

ALTER TABLE places
    ADD CONSTRAINT uq_place_code UNIQUE (code);

ALTER TABLE route_nodes
    ADD COLUMN point_no VARCHAR(20) NULL AFTER building_id;

ALTER TABLE route_nodes
    ADD COLUMN code VARCHAR(100) NULL AFTER point_no;

ALTER TABLE route_nodes
    ADD CONSTRAINT uq_route_node_code UNIQUE (code);
