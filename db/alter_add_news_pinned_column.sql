-- ============================================
-- news.pinned: 게시판 상단 고정 공지인지(2026-10-12, 국제협력본부 게시판 추가와 함께).
--
-- 크롤러가 상단 고정 행을 구분하는 게시판(지금은 국제협력본부 inno.hongik.ac.kr/oia)에서만
-- 크롤링할 때마다 첫 페이지 고정 목록으로 맞춘다. 앱 게시판 화면은 고정 글을 목록 위에 따로 묶어 보여 주고
-- 본 목록(GET /news?pinned=false)에서는 뺀다. 그 밖의 게시판 글은 항상 FALSE.
--
-- NOT NULL DEFAULT FALSE라 기존 행이 있어도 안전하게 추가되고, 기존 글은 모두 고정 아님으로 시작한다.
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 RDS에 직접 실행해야 함.
-- 이 스크립트를 실행하지 않고 새 엔티티만 배포하면 컬럼 불일치로 앱이 기동에 실패한다.
-- (순서: 1) 이 스크립트 실행 → 2) 새 백엔드 배포)
-- ============================================

ALTER TABLE news
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE AFTER published_at;

-- 확인용
-- SELECT source_id, title FROM news WHERE pinned = TRUE;
