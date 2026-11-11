-- ============================================
-- 총학생회 제휴 중 빠진 곳 반영 (2026-11-10).
-- 출처: 제60대 총학생회 태양 인스타그램(@hongik_chance) '제휴 업체 한 눈에 보기' 주점·카페·외식·문화 게시글
--       사용자 캡처 IMG_9100~9122(45곳). 이미 총학생회 소속으로 있는 39곳은 건드리지 않는다.
--
-- - 새 업체 5곳: 미오미와, 닭장맥주 홍대점(주점), 마이미 라운지, 유어데이(카페), 캣툰 홍대점(문화).
--   좌표는 도로명 주소를 OpenStreetMap(Nominatim)으로 변환한 건물 위치(5곳 모두 번지까지 일치).
--   기존 총학생회 제휴처럼 종료일 없음, 근처 알림 대상 아님(nearby_alert FALSE, 관리자가 고른다).
-- - 이미 있는 업체: 푸줏간살롱(경영대학만 있음)에 총학생회 소속과 게시글 혜택을 더한다. 주소·좌표·기본 혜택은 그대로.
-- - 넣지 않은 것: 왓챠(온라인 구독권, 지도에 찍을 위치 없음).
-- - 게시글 8~10/11쪽(발바리네·해정집·더기와 합정·대목·스케줄 합정)은 주소·혜택이 앞 쪽 업체 문구를 그대로 복사한
--   것으로 보여(게시글 오류) 기존 값을 그대로 둔다. 다섯 곳 모두 이미 총학생회 소속으로 있다.
-- - 다시 실행해도 안전하다: 이미 있는 code·소속은 건너뛴다.
-- - 관리자 API 가 아닌 SQL 이라 지도 데이터 캐시는 최대 5분 뒤에 바뀐다.
-- ============================================

SET @base := (SELECT COALESCE(MAX(sort_order), 0) FROM partners);

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, nearby_alert)
SELECT 'bar-miomiwa', '미오미와', '주점', 37.5509682, 126.9236438,
       '총 금액 10% 할인', '서울 마포구 와우산로18길 15 1층', NULL, @base + 1, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-miomiwa');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, nearby_alert)
SELECT 'bar-dakjang-beer-hongdae', '닭장맥주 홍대점', '주점', 37.5529102, 126.9213353,
       '총 금액 10% 할인', '서울 마포구 양화로16길 34 1층 103호', NULL, @base + 2, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-dakjang-beer-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, nearby_alert)
SELECT 'cafe-mymi-lounge', '마이미 라운지', '카페', 37.5479540, 126.9243210,
       '전 메뉴 10% 할인', '서울 마포구 독막로 97 1층', NULL, @base + 3, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-mymi-lounge');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, nearby_alert)
SELECT 'cafe-yourday', '유어데이', '카페', 37.5482473, 126.9213329,
       '월~금 홀 이용 고객 1만 원 이상 구매 시 10% 할인', '서울 마포구 와우산로13길 49-11 1층', NULL, @base + 4, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-yourday');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, nearby_alert)
SELECT 'culture-cattoon-hongdae', '캣툰 홍대점', '문화', 37.5516140, 126.9216017,
       '총 금액 10% 할인', '서울 마포구 어울마당로 74 5층', NULL, @base + 5, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-cattoon-hongdae');

INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '총학생회', NULL, NULL FROM partners p
WHERE p.code IN ('bar-miomiwa', 'bar-dakjang-beer-hongdae', 'cafe-mymi-lounge', 'cafe-yourday', 'culture-cattoon-hongdae')
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '총학생회');

-- 푸줏간살롱: 경영대학 제휴에 총학생회 소속과 전용 혜택 더하기
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '총학생회', '2인분 이상 주문 시 급냉삼겹살 1인분 제공', NULL FROM partners p
WHERE p.code = 'food-pujutgan-salon'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '총학생회');

-- 확인용
-- SELECT p.code, p.name, p.category, a.affiliation, a.benefit FROM partners p JOIN partner_affiliations a ON a.partner_id = p.id
-- WHERE p.code IN ('bar-miomiwa','bar-dakjang-beer-hongdae','cafe-mymi-lounge','cafe-yourday','culture-cattoon-hongdae','food-pujutgan-salon');
