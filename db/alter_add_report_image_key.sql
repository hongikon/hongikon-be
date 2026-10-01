-- ============================================
-- reports.image_key: 제보 사진 1장의 S3 객체 키 (예: reports/2f1c...-uuid.jpg). 사진이 없으면 NULL.
-- URL 이 아니라 키만 저장한다 — 버킷은 비공개이고, 응답할 때마다 presigned GET URL 을 새로 만든다.
-- ddl-auto=validate 환경이므로 이 PR 을 배포하기 전에 RDS 에 먼저 실행해야 함.
-- 이전 코드는 이 컬럼을 모르므로(엔티티에 없는 컬럼은 validate 가 무시) 먼저 실행해도 안전하다.
-- ============================================

ALTER TABLE reports
    ADD COLUMN image_key VARCHAR(200) NULL AFTER content;
