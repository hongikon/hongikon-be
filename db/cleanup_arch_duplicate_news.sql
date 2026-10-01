-- ============================================
-- 건축학부(arch.hongik.ac.kr) 중복 소식 정리 (1회성)
--
-- 원인: 건축학부 CMS는 상세 링크의 idx·code를 요청마다 새로 암호화해서, 같은 글도 크롤링할 때마다
-- source_url이 달랐다. 크롤러가 source_url로 중복을 판단해 매 주기마다 같은 글을 새 행으로 저장했다.
-- (코드 수정: ArchBoardParser.hasStableArticleUrl() = false → 게시판 출처+제목+작성일로 중복 판단)
--
-- 정리 기준: 같은 게시판(notice.php / event.php) + 같은 제목 + 같은 작성일 묶음에서 가장 먼저 저장된 행(MIN(id))만 남긴다.
-- 예전 링크도 계속 열리므로 남는 행의 source_url은 그대로 써도 된다.
--
-- 참조 테이블:
--   bookmarks(news_id, UNIQUE(user_id, news_id)) — 남길 행으로 옮기고, 이미 북마크가 있어 옮길 수 없는 건 지운다.
--   notifications(news_id, ON DELETE SET NULL)   — 지우기 전에 남길 행으로 옮긴다(안 옮기면 news_id가 NULL이 된다).
--
-- 순서: 1) 새 백엔드 배포(중복이 더 쌓이지 않게) → 2) 미리보기 확인 → 3) 트랜잭션으로 실행 → 4) 확인 후 COMMIT
-- 같은 세션(연결)에서 끝까지 실행해야 한다(임시 테이블 사용).
-- ============================================

-- 0) 미리보기: 게시판별 전체 행 수 vs 실제 글 수
SELECT SUBSTRING_INDEX(source_url, '?', 1) AS board,
       COUNT(*) AS total_rows,
       COUNT(DISTINCT title, published_at) AS unique_posts
  FROM news
 WHERE source_url LIKE 'https://arch.hongik.ac.kr/%'
 GROUP BY board;

-- 1) 지울 행(dup_id) → 남길 행(keep_id) 매핑
DROP TEMPORARY TABLE IF EXISTS arch_dup_map;
CREATE TEMPORARY TABLE arch_dup_map (
    dup_id  BIGINT PRIMARY KEY,
    keep_id BIGINT NOT NULL
);
INSERT INTO arch_dup_map (dup_id, keep_id)
SELECT n.id, k.keep_id
  FROM news n
  JOIN (SELECT SUBSTRING_INDEX(source_url, '?', 1) AS board, title, published_at, MIN(id) AS keep_id
          FROM news
         WHERE source_url LIKE 'https://arch.hongik.ac.kr/%'
         GROUP BY board, title, published_at) k
    ON SUBSTRING_INDEX(n.source_url, '?', 1) = k.board
   AND n.title = k.title
   AND n.published_at = k.published_at
 WHERE n.source_url LIKE 'https://arch.hongik.ac.kr/%'
   AND n.id <> k.keep_id;

SELECT COUNT(*) AS rows_to_delete FROM arch_dup_map;
SELECT COUNT(*) AS bookmarks_affected FROM bookmarks b JOIN arch_dup_map m ON b.news_id = m.dup_id;
SELECT COUNT(*) AS notifications_affected FROM notifications t JOIN arch_dup_map m ON t.news_id = m.dup_id;

-- 2) 실행
START TRANSACTION;

-- 북마크: 남길 행으로 옮긴다. 같은 유저가 이미 남길 행을 북마크했으면 UNIQUE 충돌로 건너뛴다(IGNORE).
UPDATE IGNORE bookmarks b
  JOIN arch_dup_map m ON b.news_id = m.dup_id
   SET b.news_id = m.keep_id;
-- 옮기지 못하고 남은(=같은 유저가 이미 북마크한) 중복 북마크는 지운다.
DELETE b FROM bookmarks b JOIN arch_dup_map m ON b.news_id = m.dup_id;

-- 알림: 남길 행으로 옮긴다.
UPDATE notifications t
  JOIN arch_dup_map m ON t.news_id = m.dup_id
   SET t.news_id = m.keep_id;

-- 중복 소식 삭제
DELETE n FROM news n JOIN arch_dup_map m ON n.id = m.dup_id;

-- 3) 확인: total_rows = unique_posts 여야 한다
SELECT SUBSTRING_INDEX(source_url, '?', 1) AS board,
       COUNT(*) AS total_rows,
       COUNT(DISTINCT title, published_at) AS unique_posts
  FROM news
 WHERE source_url LIKE 'https://arch.hongik.ac.kr/%'
 GROUP BY board;

-- 결과가 맞으면 COMMIT, 이상하면 ROLLBACK
-- COMMIT;
-- ROLLBACK;
