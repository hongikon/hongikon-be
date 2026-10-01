-- ============================================
-- news.source_id: 이 글을 수집한 게시판의 sourceId(BoardConfig.sourceId)를 저장한다.
-- 프론트 TREE_DATA 리프 id와 같은 값이다 — 학과 게시판은 학과명(예: '컴퓨터공학과'),
-- 대학공지는 분류 라벨('학사','장학','교수학습지원','학생상담','대학혁신지원사업','학생활동').
--
-- 왜 필요한가: 대학공지는 departments 테이블에 없어서(seed_departments.sql 참고) department_id가
-- 항상 NULL이다. 그래서 프론트가 departmentName으로 구독 필터링을 하면 '학사'/'장학' 등을
-- 구독해도 아무 글도 안 보인다. 게시판 출처를 따로 저장해 프론트가 이 값으로 필터링하게 한다.
--
-- nullable이라 기존 행이 있어도 안전하게 추가 가능하다.
--
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 실제 RDS에 직접 실행해야 함.
-- 이 스크립트를 실행하지 않고 새 엔티티만 배포하면 컬럼 불일치로 앱이 기동에 실패한다.
-- (순서: 1) 이 스크립트 실행 → 2) 새 백엔드 배포)
-- ============================================

ALTER TABLE news
    ADD COLUMN source_id VARCHAR(50) NULL AFTER source_url;

-- ============================================
-- 기존 행 백필 (1회성, 여러 번 실행해도 안전 — source_id IS NULL 인 행만 건드린다)
--
-- 1) 대학공지: 상세 링크의 noCat 파라미터로 분류를 정확히 복원할 수 있다. 목록 페이지
--    (education/notice-undergrad.do?srCategoryId=N)의 글 링크는 두 형태로 저장돼 있다(2026-09-30 실측):
--      - 일반 글: https://www.hongik.ac.kr/kr/newscenter/notice.do?mode=view&articleNo=158541&noCat=500
--      - 상단 고정 글: https://www.hongik.ac.kr/kr/education/notice-undergrad.do?mode=view&articleNo=157133&...&srCategoryId=24&noCat=24
--    noCat ↔ 분류 (FE src/data/news.cs.json 정적 데이터 분류와도 일치 확인):
--      학사 500/23, 장학 501/24, 교수학습지원 511/534, 학생상담 512/535, 대학혁신지원사업 513/536, 학생활동 514/537
--    '23'이 '234' 등에 잘못 걸리지 않도록 정규식으로 값 끝(& 또는 문자열 끝)까지 맞춘다.
--
-- 2) 학과 게시판: source_url 호스트로 게시판을 역추정한다(NewsLocationMatcher.matchDepartmentBySourceUrl과
--    같은 논리). 같은 호스트의 하위 게시판(-job/-doc/-gen/-ev)은 모두 같은 sourceId라 호스트 하나로 충분하다.
--    호스트→sourceId 목록은 CrawlerBoards.DEPARTMENT_BOARDS에서 그대로 옮겼다.
--
-- 한계: 위 두 규칙에 안 걸리는 행(URL 형식이 예외적인 글 등)은 NULL로 남는다. 대신 배포 후
--    시간당 크롤러가 각 게시판의 최신 글(최대 약 100건)을 다시 만날 때 source_id가 비어 있으면
--    채우도록 했다(CrawlerService → NewsCrawlStorageService.fillMissingSourceId). 그보다 오래된 글이
--    끝까지 NULL이면 프론트는 departmentName → '기타' 순으로 폴백한다.
--
-- MySQL Workbench의 safe update 모드에서는 키 없는 UPDATE가 막히므로 필요하면
-- SET SQL_SAFE_UPDATES = 0; 후 실행한다.
-- ============================================

-- 1) 대학공지 (www.hongik.ac.kr, noCat 기준)
UPDATE news SET source_id = '학사'
 WHERE source_id IS NULL
   AND source_url LIKE 'https://www.hongik.ac.kr/%'
   AND source_url REGEXP '[?&]noCat=(500|23)(&|$)';
UPDATE news SET source_id = '장학'
 WHERE source_id IS NULL
   AND source_url LIKE 'https://www.hongik.ac.kr/%'
   AND source_url REGEXP '[?&]noCat=(501|24)(&|$)';
UPDATE news SET source_id = '교수학습지원'
 WHERE source_id IS NULL
   AND source_url LIKE 'https://www.hongik.ac.kr/%'
   AND source_url REGEXP '[?&]noCat=(511|534)(&|$)';
UPDATE news SET source_id = '학생상담'
 WHERE source_id IS NULL
   AND source_url LIKE 'https://www.hongik.ac.kr/%'
   AND source_url REGEXP '[?&]noCat=(512|535)(&|$)';
UPDATE news SET source_id = '대학혁신지원사업'
 WHERE source_id IS NULL
   AND source_url LIKE 'https://www.hongik.ac.kr/%'
   AND source_url REGEXP '[?&]noCat=(513|536)(&|$)';
UPDATE news SET source_id = '학생활동'
 WHERE source_id IS NULL
   AND source_url LIKE 'https://www.hongik.ac.kr/%'
   AND source_url REGEXP '[?&]noCat=(514|537)(&|$)';

-- 2) 학과 게시판 (호스트 기준)
UPDATE news SET source_id = '컴퓨터공학과' WHERE source_id IS NULL AND source_url LIKE 'https://wwwce.hongik.ac.kr/%';
UPDATE news SET source_id = '전자전기공학부' WHERE source_id IS NULL AND source_url LIKE 'https://ee.hongik.ac.kr/%';
UPDATE news SET source_id = '신소재공학전공' WHERE source_id IS NULL AND source_url LIKE 'https://mse.hongik.ac.kr/%';
UPDATE news SET source_id = '화학공학전공' WHERE source_id IS NULL AND source_url LIKE 'https://chemeng.hongik.ac.kr/%';
UPDATE news SET source_id = '산업데이터공학과' WHERE source_id IS NULL AND source_url LIKE 'https://ie.hongik.ac.kr/%';
UPDATE news SET source_id = '기계시스템디자인공학과' WHERE source_id IS NULL AND source_url LIKE 'https://me.hongik.ac.kr/%';
UPDATE news SET source_id = '건설환경공학과' WHERE source_id IS NULL AND source_url LIKE 'https://civil.hongik.ac.kr/%';
UPDATE news SET source_id = '건축학부' WHERE source_id IS NULL AND source_url LIKE 'https://arch.hongik.ac.kr/%';
UPDATE news SET source_id = '도시학과' WHERE source_id IS NULL AND source_url LIKE 'https://urban.hongik.ac.kr/%';
UPDATE news SET source_id = '경제학부' WHERE source_id IS NULL AND source_url LIKE 'https://economics.hongik.ac.kr/%';
UPDATE news SET source_id = '경영학부' WHERE source_id IS NULL AND source_url LIKE 'https://bizadmin.hongik.ac.kr/%';
UPDATE news SET source_id = '영어영문학과' WHERE source_id IS NULL AND source_url LIKE 'https://english.hongik.ac.kr/%';
UPDATE news SET source_id = '독어독문학과' WHERE source_id IS NULL AND source_url LIKE 'https://german.hongik.ac.kr/%';
UPDATE news SET source_id = '불어불문학과' WHERE source_id IS NULL AND source_url LIKE 'https://france.hongik.ac.kr/%';
UPDATE news SET source_id = '국어국문학과' WHERE source_id IS NULL AND source_url LIKE 'https://hkorean.hongik.ac.kr/%';
UPDATE news SET source_id = '법학부' WHERE source_id IS NULL AND source_url LIKE 'https://law.hongik.ac.kr/%';
UPDATE news SET source_id = '교육학과' WHERE source_id IS NULL AND source_url LIKE 'https://edu.hongik.ac.kr/%';
UPDATE news SET source_id = '국어교육과' WHERE source_id IS NULL AND source_url LIKE 'https://koredu.hongik.ac.kr/%';
UPDATE news SET source_id = '수학교육과' WHERE source_id IS NULL AND source_url LIKE 'https://math.hongik.ac.kr/%';
UPDATE news SET source_id = '영어교육과' WHERE source_id IS NULL AND source_url LIKE 'https://engedu.hongik.ac.kr/%';
UPDATE news SET source_id = '역사교육과' WHERE source_id IS NULL AND source_url LIKE 'https://hisedu.hongik.ac.kr/%';
UPDATE news SET source_id = '동양화과' WHERE source_id IS NULL AND source_url LIKE 'https://orip.hongik.ac.kr/%';
UPDATE news SET source_id = '회화과' WHERE source_id IS NULL AND source_url LIKE 'https://painting.hongik.ac.kr/%';
UPDATE news SET source_id = '판화과' WHERE source_id IS NULL AND source_url LIKE 'https://printmk.hongik.ac.kr/%';
UPDATE news SET source_id = '조소과' WHERE source_id IS NULL AND source_url LIKE 'https://scu.hongik.ac.kr/%';
UPDATE news SET source_id = '디자인학부' WHERE source_id IS NULL AND source_url LIKE 'https://id.hongik.ac.kr/%';
UPDATE news SET source_id = '금속조형디자인과' WHERE source_id IS NULL AND source_url LIKE 'https://metalart.hongik.ac.kr/%';
UPDATE news SET source_id = '도예유리과' WHERE source_id IS NULL AND source_url LIKE 'https://cer.hongik.ac.kr/%';
UPDATE news SET source_id = '목조형가구학과' WHERE source_id IS NULL AND source_url LIKE 'https://waf.hongik.ac.kr/%';
UPDATE news SET source_id = '섬유미술패션디자인과' WHERE source_id IS NULL AND source_url LIKE 'https://textile.hongik.ac.kr/%';
UPDATE news SET source_id = '예술학과' WHERE source_id IS NULL AND source_url LIKE 'https://art.hongik.ac.kr/%';
UPDATE news SET source_id = '뮤지컬전공' WHERE source_id IS NULL AND source_url LIKE 'https://musical.hongik.ac.kr/%';
UPDATE news SET source_id = '실용음악전공' WHERE source_id IS NULL AND source_url LIKE 'https://music.hongik.ac.kr/%';
UPDATE news SET source_id = '디자인예술경영학부' WHERE source_id IS NULL AND source_url LIKE 'https://iim.hongik.ac.kr/%';

-- 확인용: 남은 NULL 건수와 게시판별 분포
-- SELECT source_id, COUNT(*) FROM news GROUP BY source_id ORDER BY COUNT(*) DESC;
