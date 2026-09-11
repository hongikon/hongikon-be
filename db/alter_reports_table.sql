-- ============================================
-- reports: 확정 스펙 반영 (2026-09-08)
--   - building_id/floor 필수화 (건물+층이 정보의 핵심)
--   - custom_category_label 추가 (category=ETC일 때 자유 텍스트 세분화)
--   - status 기본값 PENDING으로 변경 (ACTIVE/REJECTED/HIDDEN/DELETED와 함께 5종)
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 DB에 직접 실행해야 함.
-- 기존에 building_id 또는 floor가 NULL인 행이 있다면 NOT NULL 적용 전에 먼저 정리(백필/삭제)할 것.
-- ============================================

ALTER TABLE reports
    ADD COLUMN custom_category_label VARCHAR(50) NULL AFTER category;

ALTER TABLE reports
    MODIFY COLUMN building_id BIGINT NOT NULL,
    MODIFY COLUMN floor INT NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- building_id에 FK 제약이 아직 없다면 추가 (이미 있다면 아래 문장은 건너뛸 것)
ALTER TABLE reports
    ADD CONSTRAINT fk_reports_building FOREIGN KEY (building_id) REFERENCES buildings (id) ON DELETE CASCADE;
