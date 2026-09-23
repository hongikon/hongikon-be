-- ============================================
-- news: 크롤러가 파싱은 하지만 저장 단계에서 버려지던 이미지/첨부파일/조회수를
-- 실제로 저장하기 위해 컬럼 3개를 추가한다.
--
--   - news.images      : 본문에 박힌 이미지 URL 목록(JSON 배열 문자열)  — News.java
--   - news.attachments : 첨부파일(이름+URL) 목록(JSON 배열 문자열)      — News.java
--   - news.views       : 원문 게시글 조회수. 못 읽으면 NULL             — News.java
--
-- 전부 nullable이라 기존 행이 있어도 안전하게 추가 가능(기존 행은 NULL로 남고,
-- News.java의 images/attachments 컨버터가 NULL을 빈 리스트로 되돌려준다).
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 RDS에 직접 실행해야 함.
-- 이 스크립트를 실행하지 않고 새 엔티티만 배포하면 컬럼 불일치로 앱이 기동에 실패한다.
-- ============================================

ALTER TABLE news
    ADD COLUMN images TEXT NULL AFTER content;

ALTER TABLE news
    ADD COLUMN attachments TEXT NULL AFTER images;

ALTER TABLE news
    ADD COLUMN views INT NULL AFTER attachments;
