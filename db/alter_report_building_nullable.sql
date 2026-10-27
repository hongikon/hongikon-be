-- ============================================
-- reports.building_id·floor NULL 허용 (2026-10-27, 캠퍼스 밖 제보).
--
-- 홍대 생활권(캠퍼스 중심 2.5km 안)의 학교 건물이 없는 곳도 제보할 수 있게 한다. 그런 제보는 건물·층이 없고
-- 장소 설명(place_label)이 필수다(ReportService.checkOffCampus). 캠퍼스 안 제보는 지금처럼 건물·층 필수.
--
-- NOT NULL 을 푸는 것뿐이라 기존 행·지금 서버에 영향이 없다 — 배포 전에 먼저 실행한다.
-- 이 스크립트 없이 새 서버만 올리면 기동은 되지만(validate 는 NULL 여부를 보지 않는다) 캠퍼스 밖 제보 등록이 500 이 난다.
-- 외래 키(building_id → buildings)는 그대로 둔다.
-- ============================================

ALTER TABLE reports
    MODIFY building_id BIGINT NULL,
    MODIFY floor INT NULL;

-- 확인: 둘 다 Null = YES
SHOW COLUMNS FROM reports WHERE Field IN ('building_id', 'floor');

-- 되돌리기(캠퍼스 밖 제보가 한 건도 없을 때만 가능):
-- SELECT COUNT(*) FROM reports WHERE building_id IS NULL OR floor IS NULL;   -- 0 이어야 한다
-- ALTER TABLE reports MODIFY building_id BIGINT NOT NULL, MODIFY floor INT NOT NULL;
