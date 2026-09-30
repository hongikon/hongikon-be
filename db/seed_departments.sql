-- ============================================
-- departments 시드: TREE_DATA(프론트 뉴스 구독 트리) 43개 리프 전체 반영
-- 작성: 2026-09-11, 프론트 TREE_DATA + 백엔드 CrawlerBoards.java 대조 + 홍익대 홈페이지 확인 기반
--
-- 규칙:
--   - name: 크롤러 sourceId와 완전일치해야 하는 항목은 sourceId 그대로(가운뎃점 등 표기 차이 있으면 sourceId 우선)
--   - college: TREE_DATA 최상위 그룹명과 완전일치
--   - kind: 전부 'department' (UNIVERSITY_BOARDS 6개 기관 게시판은 애초에 여기 대상 아님, department_id NULL이 정상)
--
-- 발견된 오류 수정 사항 (크롤러 개발 시 추정이 TREE_DATA와 달랐던 것들):
--   - 경제학부: 경영대학(추정) -> 경제학부(독립 편제, 확정)
--   - 국어국문학과/독어독문학과/불어불문학과/영어영문학과: 인문대학(추정) -> 문과대학(확정)
--   - 디자인학부/금속조형디자인과/도예유리과/목조형가구학과/섬유미술패션디자인과: 디자인대학(추정, 존재하지 않는 그룹) -> 미술대학(확정)
--   - 뮤지컬전공/실용음악전공: 불확실 -> 공연예술학부(확정)
--   - 디자인예술경영학부: 불확실 -> 디자인예술경영학부(자체, 확정)
--
-- 표기 차이 주의 (크롤러 sourceId에 가운뎃점 없음, TREE_DATA 표시엔 있음):
--   - 산업데이터공학과 (TREE 표시: 산업·데이터공학과)
--   - 기계시스템디자인공학과 (TREE 표시: 기계·시스템디자인공학과)
--   -> 구독 API가 TREE_DATA 표시명 그대로 조회하면 못 찾을 수 있음, 별도 확인 필요(오늘 범위 밖)
--
-- 크롤러 게시판 없어 department_id가 계속 NULL로 남을 것으로 예상되는 항목 (참고용, 시드 자체는 진행):
--   - 사물인터넷공학전공, 지능로봇공학전공, 데이터사이언스전공 (융합전공, 자체 게시판 없음)
--   - 디자인경영전공, 예술경영전공 (학부 공용 게시판이라 하위 전공 구분 불가)
--
-- 신규 크롤러 게시판 후보 (실존 확인됨, 오늘 범위 밖 별도 작업):
--   - 바이오헬스융합학부: biocoss.hongik.ac.kr/biocoss/0401.do
--   - 기초과학과: science.hongik.ac.kr/science/0401.do
--   - 디자인엔지니어링전공: smpd.hongik.ac.kr/smpd/0401.do
--   - 캠퍼스자율전공(서울): fm.hongik.ac.kr/fm/0401.do (단, TREE_DATA 뉴스 트리엔 없음 - PARTNER_AFFILIATIONS에만 존재)
-- ============================================

-- 건축도시대학
INSERT INTO departments (name, college, kind) VALUES ('건축학부', '건축도시대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('도시학과', '건축도시대학', 'department');

-- 경영대학
INSERT INTO departments (name, college, kind) VALUES ('경영학부', '경영대학', 'department');

-- 경제학부 (독립 편제)
INSERT INTO departments (name, college, kind) VALUES ('경제학부', '경제학부', 'department');

-- 공과대학
INSERT INTO departments (name, college, kind) VALUES ('건설환경공학과', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('기계시스템디자인공학과', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('기초과학과', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('산업데이터공학과', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('신소재공학전공', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('화학공학전공', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('전자전기공학부', '공과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('컴퓨터공학과', '공과대학', 'department');

-- 공연예술학부
INSERT INTO departments (name, college, kind) VALUES ('뮤지컬전공', '공연예술학부', 'department');
INSERT INTO departments (name, college, kind) VALUES ('실용음악전공', '공연예술학부', 'department');

-- 디자인예술경영학부
INSERT INTO departments (name, college, kind) VALUES ('디자인예술경영학부', '디자인예술경영학부', 'department');
INSERT INTO departments (name, college, kind) VALUES ('디자인경영전공', '디자인예술경영학부', 'department');
INSERT INTO departments (name, college, kind) VALUES ('예술경영전공', '디자인예술경영학부', 'department');

-- 문과대학
INSERT INTO departments (name, college, kind) VALUES ('국어국문학과', '문과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('독어독문학과', '문과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('불어불문학과', '문과대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('영어영문학과', '문과대학', 'department');

-- 미술대학
INSERT INTO departments (name, college, kind) VALUES ('금속조형디자인과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('도예유리과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('동양화과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('디자인학부', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('목조형가구학과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('섬유미술패션디자인과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('예술학과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('자율전공', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('조소과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('판화과', '미술대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('회화과', '미술대학', 'department');

-- 바이오헬스융합학부 (독립 학부)
INSERT INTO departments (name, college, kind) VALUES ('바이오헬스융합학부', '바이오헬스융합학부', 'department');

-- 법과대학
INSERT INTO departments (name, college, kind) VALUES ('법학부', '법과대학', 'department');

-- 사범대학
INSERT INTO departments (name, college, kind) VALUES ('교육학과', '사범대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('국어교육과', '사범대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('수학교육과', '사범대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('역사교육과', '사범대학', 'department');
INSERT INTO departments (name, college, kind) VALUES ('영어교육과', '사범대학', 'department');

-- 융합전공 (자체 게시판 없는 것들 포함, department_id는 당분간 NULL로 남을 수 있음)
INSERT INTO departments (name, college, kind) VALUES ('데이터사이언스전공', '융합전공', 'department');
INSERT INTO departments (name, college, kind) VALUES ('디자인엔지니어링전공', '융합전공', 'department');
INSERT INTO departments (name, college, kind) VALUES ('사물인터넷공학전공', '융합전공', 'department');
INSERT INTO departments (name, college, kind) VALUES ('지능로봇공학전공', '융합전공', 'department');
