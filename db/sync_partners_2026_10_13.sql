-- ============================================
-- 총학생회 제휴 추가·갱신 (2026-10-13). 출처: 총학생회 제휴 안내 게시글(@hongik_chance) 9건.
-- 선행: db/alter_add_partner_ends_on.sql (partners.ends_on)
--
-- - 새 업체 8곳 추가, 보그헤어 상수역점은 혜택·영업시간만 갱신(좌표·주소 그대로).
-- - 좌표는 도로명 주소를 OpenStreetMap(Nominatim)으로 변환한 건물 위치.
-- - 월리를 찾아라 체험관은 12/31 까지인 기간 한정 제휴라 ends_on 을 넣었다 — 2027-01-01 부터 지도에서 자동으로 빠진다.
-- - 다시 실행해도 안전하다: 이미 있는 code 는 건너뛰고, 보그헤어 UPDATE 는 같은 값으로 덮는다.
-- - 관리자 API 가 아닌 SQL 이라 지도 데이터 캐시는 최대 5분 뒤에 바뀐다.
-- ============================================

SET @base := (SELECT COALESCE(MAX(sort_order), 0) FROM partners);

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-schedule-hapjeong', '스케줄 합정', '음식', 37.5491070, 126.9160804, '테이블 총 결제금액 20% 할인', '서울 마포구 양화로6길 33 1층', '매일 11:00~22:00 (브레이크 타임 15:00~16:00)', @base + 1, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-schedule-hapjeong');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'bar-gonggam-hapjeong', '공감 합정', '주점', 37.5489158, 126.9170257, '테이블 총 결제금액 10% 할인, 3:3 이상 과팅 진행 시 메인 오뎅탕 제공 (6인 이상 과팅 혜택과 10% 할인은 중복 적용 불가)', '서울 마포구 양화로6길 49 2층', '매일 16:00~02:00 (라스트 오더 01:00)', @base + 2, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-gonggam-hapjeong');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'bar-gonggam-hongdae', '공감 홍대', '주점', 37.5502998, 126.9225084, '테이블 총 결제금액 10% 할인, 3:3 이상 과팅 진행 시 메인 오뎅탕 제공 (6인 이상 과팅 혜택과 10% 할인은 중복 적용 불가)', '서울 마포구 와우산로17길 10 2층', '매일 16:00~02:00 (라스트 오더 01:00)', @base + 3, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-gonggam-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-daemok', '대목', '음식', 37.5489307, 126.9171987, '테이블 총 결제금액 10% 할인', '서울 마포구 양화로6길 51 1층, 2층', '매일 11:30~23:30 (브레이크 타임 15:00~17:00)', @base + 4, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-daemok');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-thegiwa-hapjeong', '더기와 합정', '음식', 37.5495789, 126.9169406, '테이블 총 결제금액 10% 할인, 3:3 이상 과팅 진행 시 우베막걸리·말차막걸리·티라미수막걸리 중 1개 제공 (6인 이상 과팅 혜택과 10% 할인은 중복 적용 불가)', '서울 마포구 독막로3길 25 1층', '매일 11:30~01:30', @base + 5, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-thegiwa-hapjeong');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-haejeongjip', '해정집', '음식', 37.5489756, 126.9167492, '테이블 총 결제금액 10% 할인', '서울 마포구 양화로6길 45 1층', '매일 10:00~23:00 (브레이크 타임 15:00~17:00)', @base + 6, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-haejeongjip');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'culture-wally-experience', '월리를 찾아라 체험관', '문화', 37.5520324, 126.9237281, '정가 1인 25,000원 → 12,500원 (50% 할인), 12월 31일까지', '서울 마포구 와우산로 86', '매일 10:00~21:00 (입장 마감 20:00)', @base + 7, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-wally-experience');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'culture-everland', '에버랜드', '문화', 37.2934745, 127.2024789, '정상가 62,000~71,000원 → 35,000원 (구매 10월 31일까지, 이용 11월 22일까지). 총학생회 게시물의 QR코드와 인증코드로 구매', '경기도 용인시 처인구 포곡읍 에버랜드로 199', '매일 10:00~22:00', @base + 8, NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-everland');

INSERT INTO partner_affiliations (partner_id, affiliation, benefit)
SELECT p.id, '총학생회', NULL FROM partners p
WHERE p.code IN ('food-schedule-hapjeong', 'bar-gonggam-hapjeong', 'bar-gonggam-hongdae', 'food-daemok',
                 'food-thegiwa-hapjeong', 'food-haejeongjip', 'culture-wally-experience', 'culture-everland')
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '총학생회');

-- 보그헤어 상수역점: 게시글 기준으로 혜택 갱신(재방문 30% → 20%, 홍대생 회원권 30% 등), 영업시간 추가.
UPDATE partners
SET benefit = '첫 방문 40% 할인(커트 10%), 재방문 20% 할인(커트 10%), 홍대생 회원권 30% 할인, 네이버 플레이스 저장·알림받기 시 클리닉 50% 할인, 예약란 코드 기재 시 추가 할인. 네이버 예약 메모에 ''홍익대학교 학생'' 기재 후 학생증 확인',
    hours = '매일 10:00~20:00'
WHERE code = 'care-vogue-hair-sangsu';

-- 확인용
-- SELECT code, name, category, latitude, longitude, hours, ends_on FROM partners ORDER BY sort_order DESC LIMIT 9;
