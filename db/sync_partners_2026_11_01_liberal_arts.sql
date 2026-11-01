-- ============================================
-- 문과대학 제휴 반영 (2026-11-01).
-- 출처: 문과대학 제46대 학생회 Link '2026 문과대학 제휴 안내' 게시글(@hongik_liberal_arts, 2026-08-22) 12곳.
--       제휴 기간 2026-09-01 ~ 2026-12-31, 실물·모바일 학생증으로 문과대학 소속 학부 확인.
-- 선행: db/alter_add_partner_ends_on.sql (partners.ends_on, partner_affiliations.ends_on)
--
-- - 새 업체 2곳: 샤브로21 상수, 눈내리는숲 설래임 홍대본점. 문과대학만 있는 업체라 업체 종료일도 2026-12-31.
-- - 이미 있는 10곳: 문과대학 소속을 게시글 혜택 문구와 소속 종료일 2026-12-31 로 넣는다.
--   소코아 홍대점·찜이로닭 상수본점은 문과대학 소속이 이미 있어 혜택·종료일만 덮는다.
-- - 주소: 타오마라탕 홍대점·마라이장원·겐로쿠우동은 지번 → 게시글 도로명(좌표는 Nominatim 으로 5m 안 같은 건물 확인),
--   제제집은 층(지1층)을 더한다. 좌표는 그대로.
-- - 하르카쿠키 게시글의 '진행 중 이벤트'(수·목·금 쿠키 1만 원 이상 아메리카노)는 기간이 없어 넣지 않았다.
-- - 키이스케이프(문과대학)는 이 게시글에 없지만 다른 출처일 수 있어 건드리지 않는다.
-- - 다시 실행해도 안전하다: 이미 있는 code·소속은 건너뛰고, UPDATE 는 같은 값으로 덮는다.
-- - 관리자 API 가 아닌 SQL 이라 지도 데이터 캐시는 최대 5분 뒤에 바뀐다.
-- ============================================

SET @base := (SELECT COALESCE(MAX(sort_order), 0) FROM partners);

-- 새 업체. 샤브로21 은 소코아 홍대점과 같은 건물(와우산로15길 49).
INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-shabro21-sangsu', '샤브로21 상수', '음식', 37.5486425, 126.9209509,
       '쿠폰 발급(사용 시 소진) — 2개: 음료 택 1 또는 사리 1개 / 4개: 에이드 또는 하이볼 1잔 / 6개: 고기 추가 / 8개: 기본 샤브로21 식사권',
       '서울 마포구 와우산로15길 49', NULL, @base + 1, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-shabro21-sangsu');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'cafe-seolraeim-hongdae', '눈내리는숲 설래임 홍대본점', '카페', 37.5498045, 126.9211729,
       '평일 전 메뉴 10% 할인', '서울 마포구 어울마당로 54 1층', NULL, @base + 2, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-seolraeim-hongdae');

INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', NULL, '2026-12-31' FROM partners p
WHERE p.code IN ('food-shabro21-sangsu', 'cafe-seolraeim-hongdae')
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 이미 등록된 업체에 문과대학 소속과 전용 혜택 더하기(소속 종료일 2026-12-31)

-- 제제집
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '2~3인 기준: 공기밥 1공기 또는 음료 1캔(택 1) / 4인 기준: 된장찌개 1그릇 또는 음료 2캔(택 1)', '2026-12-31' FROM partners p
WHERE p.code = 'food-jejejip'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 타오마라탕 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '2인당 캔음료 1잔', '2026-12-31' FROM partners p
WHERE p.code = 'food-taomalatang-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 또보겠지 떡볶이집(깐따삐아점)
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '2인분: 음료 1캔 / 3인분: 기본 사리 중 택 1(차돌·세트 사리 제외) / 4인분: 감자튀김 2종 중 택 1', '2026-12-31' FROM partners p
WHERE p.code = 'food-ttoboketji-kkantapia'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 겐로쿠우동 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '1~3인 기준: 유부초밥 또는 고기어묵만두 인당 1개(택 1) / 4인 기준: 유부초밥·고기어묵만두·타코야끼 중 인당 1개(택 1)', '2026-12-31' FROM partners p
WHERE p.code = 'food-genroku-udon-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 원조한우곱도리탕 윤반장 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '점심: 공기밥 인당 1공기 또는 우동사리·당면 또는 음료(택 1) / 저녁: 공기밥 일행당 1공기 또는 우동사리·콘치즈·주먹밥·음료 중 택 1', '2026-12-31' FROM partners p
WHERE p.code = 'food-yunbanjang-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 마라이장원
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '전 메뉴 10% 할인(고기 무게도 g 수로 측정)', '2026-12-31' FROM partners p
WHERE p.code = 'food-marai-jangwon'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 하르카쿠키
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '전 메뉴 10% 할인, 포인트 5% 적립', '2026-12-31' FROM partners p
WHERE p.code = 'cafe-harka-cookie'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 샌디빌리지
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '문과대학', '샌드위치·샐러드(음료 제외) 1만 원 이상 구매 시 10% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'food-sandy-village'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '문과대학');

-- 문과대학 소속이 이미 있는 곳: 혜택·종료일을 게시글 기준으로.
UPDATE partner_affiliations a JOIN partners p ON p.id = a.partner_id
SET a.benefit = '1인 방문 시 음료 중 택 1 무료 / 2~4인 방문 시 사이드 메뉴 중 택 1 무료', a.ends_on = '2026-12-31'
WHERE p.code = 'food-socoa-hongdae' AND a.affiliation = '문과대학';

UPDATE partner_affiliations a JOIN partners p ON p.id = a.partner_id
SET a.benefit = '주문 메뉴당 캔음료 1개 서비스', a.ends_on = '2026-12-31'
WHERE p.code = 'food-jjimirodak-sangsu' AND a.affiliation = '문과대학';

-- 주소: 게시글 도로명으로(같은 건물).
UPDATE partners SET address = '서울 마포구 와우산로21길 28 2층' WHERE code = 'food-taomalatang-hongdae';
UPDATE partners SET address = '서울 마포구 어울마당로 35 1층 1호' WHERE code = 'food-marai-jangwon';
UPDATE partners SET address = '서울 마포구 어울마당로 39 2층' WHERE code = 'food-genroku-udon-hongdae';
UPDATE partners SET address = '서울 마포구 와우산로15길 30 지1층' WHERE code = 'food-jejejip';

-- 확인용
-- SELECT p.code, p.name, a.benefit, a.ends_on FROM partner_affiliations a JOIN partners p ON p.id = a.partner_id WHERE a.affiliation = '문과대학' ORDER BY p.name;
