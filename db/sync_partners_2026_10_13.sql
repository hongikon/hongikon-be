-- ============================================
-- 총학생회·미술대학 제휴 추가·갱신 (2026-10-13).
-- 출처: 총학생회 제휴 안내 게시글(@hongik_chance) 9건,
--       미술대학 학생회 NOVA 제휴사업 '노바랑' 게시글(@hongik_art_) 7편(음식점 1·2, 카페, 주점, 화방/스튜디오, 문화, 기타),
--       경영대학 학생회 BE:st 26-2 제휴 안내(@hongik_business) 2건.
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
SELECT 'culture-wally-experience', '월리를 찾아라 체험관', '문화', 37.5520324, 126.9237281, '정가 1인 25,000원 → 12,500원 (50% 할인)', '서울 마포구 와우산로 86', '매일 10:00~21:00 (입장 마감 20:00)', @base + 7, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-wally-experience');

-- 에버랜드: 게시글의 QR코드가 가리키는 예매 페이지(https://m.site.naver.com/1QWkr → reservation.everland.com 프로모션)를 시트 하단 버튼으로 넣는다.
-- 휴대폰 화면의 QR은 그 휴대폰으로 찍을 수 없어 이미지 대신 링크로 둔다.
INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, link_label, link_url, sort_order, ends_on)
SELECT 'culture-everland', '에버랜드', '문화', 37.2934745, 127.2024789, '정상가 62,000~71,000원 → 35,000원 (구매 10월 31일까지, 이용 11월 22일까지). 아래 예매 페이지에서 총학생회 게시물의 인증코드로 구매', '경기도 용인시 처인구 포곡읍 에버랜드로 199', '매일 10:00~22:00', '에버랜드 제휴 티켓 예매하기', 'https://m.site.naver.com/1QWkr', @base + 8, NULL
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

-- ============================================
-- 미술대학 학생회 NOVA 제휴사업 '노바랑'. 제휴 기간 2026-09-06 ~ 2026-12-31, 대상은 미술대학 재·휴학생(진입 전 캠퍼스자율전공 제외),
-- 학생증(실물 또는 모바일) 제시.
-- - 새 업체 25곳: ends_on 2026-12-31 — 2027-01-01 부터 지도에서 자동으로 빠진다.
-- - 이미 다른 소속 제휴로 등록된 17곳: 업체는 그대로 두고 '미술대학' 소속과 그 소속 전용 혜택만 더한다(주소·좌표·기본 혜택은 건드리지 않는다).
--   소속 행에 ends_on 2026-12-31 을 넣어 2027-01-01 부터 미술대학 소속만 자동으로 빠진다(다른 소속 제휴는 그대로 남는다).
-- - 제휴 기간은 혜택 문구에 적지 않는다 — 앱이 종료일(ends_on)을 "제휴 기간 12월 31일까지"로 따로 보여 준다.
-- - 넣지 않은 곳: 훈훈스튜디오(독막로19길 15)·런드리고아파트(독막로19길 32)·어반플루토(영등포구 양평로22나길 3-2)는 주소가 건물 번지까지
--   변환되지 않아 좌표를 확인하지 못했다. 구공스테이는 전국 지점 대상(프로모션 코드)이라 지도에 찍을 위치가 없다.
-- - 팩토리는 게시글 카드에 '팩토리 상수점'으로 적혀 있으나 표기 오류라는 정정에 따라 '팩토리'로 넣었다. 좌표는 센터플러스 건물.
-- - 또보겠지 떡볶이는 게시글 정정(3인 혜택은 차돌 사리 외 사리 중 선택)을 반영했다.
-- ============================================

SET @base2 := (SELECT COALESCE(MAX(sort_order), 0) FROM partners);

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-geunamja-bokkeumbap', '그남자의볶음밥', '음식', 37.5547992, 126.9234738, '1인당 치즈추가 또는 음료 1개 제공', '서울 마포구 와우산로23길 43 2층', NULL, @base2 + 1, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-geunamja-bokkeumbap');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-bistro-sarangbang', '비스트로 사랑방', '음식', 37.5490169, 126.9211850, '전 메뉴 10% 할인', '서울 마포구 와우산로15길 40 2층', NULL, @base2 + 2, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-bistro-sarangbang');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-momimomi-hongdae', '모미모미 홍대본점', '음식', 37.5551450, 126.9290191, '1인당 음료수 1개 제공', '서울 마포구 와우산로29길 14-7 1층', NULL, @base2 + 3, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-momimomi-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-kimdukhu-gamjatang-hongdae', '김덕후의 뼈구이감자탕 홍대직영점', '음식', 37.5551976, 126.9286886, '전체 금액의 10% 할인', '서울 마포구 와우산로29길 24 1층', NULL, @base2 + 4, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-kimdukhu-gamjatang-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-suji-and-pasta', '수지앤파스타', '음식', 37.5487202, 126.9212747, '전체 금액에서 10% 할인', '서울 마포구 독막로15길 3-18 지하 1층', NULL, @base2 + 5, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-suji-and-pasta');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-myeonsikdang-hongik', '면식당 홍익대점', '음식', 37.5530065, 126.9249156, '1~2인: 음료 1인 1개 / 3인: 에이드 또는 음료 1인 1개 / 4명 이상: 사이드메뉴(감자고로케·가라아게 1인 1피스) 또는 에이드·음료 1인 1개', '서울 마포구 와우산로 104 2층', NULL, @base2 + 6, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-myeonsikdang-hongik');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'food-samcheongdang-hongdae', '삼청당 홍대본점', '음식', 37.5531199, 126.9262738, '캔음료 1개 제공', '서울 마포구 와우산로 116 1층', NULL, @base2 + 7, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'food-samcheongdang-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'cafe-kkuul-gwajajeom', '꾸울과자점', '카페', 37.5494100, 126.9241018, '전 메뉴 10% 할인(5만원 제한, 중복 할인 불가)', '서울 마포구 와우산로22길 70 102호', NULL, @base2 + 8, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-kkuul-gwajajeom');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'cafe-beargels', '베어글스', '카페', 37.5480302, 126.9209184, '전 메뉴 10% 할인', '서울 마포구 독막로 65-1 1층', NULL, @base2 + 9, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-beargels');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'cafe-acai-monkey', '아사이몽키 아사이볼', '카페', 37.5534198, 126.9219388, '전 메뉴 10% 할인', '서울 마포구 홍익로3길 19', NULL, @base2 + 10, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-acai-monkey');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'bar-jahwasang', '자화상', '주점', 37.5620588, 126.9250063, '시그니처 1샷 제공, 행사 개최 시 공간대여비 50% 할인', '서울 마포구 동교로38길 13 3층', NULL, @base2 + 11, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-jahwasang');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'bar-bako-jumak', '바코주막', '주점', 37.5491445, 126.9190235, '짜파게티 또는 팥빙수 중 택 1 제공', '서울 마포구 독막로7길 27 1층', NULL, @base2 + 12, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-bako-jumak');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'bar-taroya', '타로야', '주점', 37.5488431, 126.9232381, '전체 금액에서 10% 할인', '서울 마포구 와우산로 50 2층', NULL, @base2 + 13, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'bar-taroya');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'etc-magazine-land', '매거진랜드', '기타', 37.5522464, 126.9201060, '전체 금액에서 10% 할인', '서울 마포구 잔다리로6길 17', NULL, @base2 + 14, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'etc-magazine-land');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'etc-hana-hwabang', '하나화방', '기타', 37.5531095, 126.9268187, '액자 제작 주문 시 10% 할인, 단체 주문 시 배달 서비스 제공', '서울 마포구 와우산로 120 1층', NULL, @base2 + 15, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'etc-hana-hwabang');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'culture-rgbcube', '알지비큐브', '문화', 37.5548292, 126.9266246, '대관료 10% 할인 (개인전, 그룹전, 학과·동아리 연합전시, 워크숍, 학생회 주관 행사 등 미술대학 관련 전시 전반)', '서울 마포구 와우산로29라길 26 1층', NULL, @base2 + 16, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-rgbcube');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'etc-pactory', '팩토리', '기타', 37.5120751, 126.8970211, '계좌이체 포함 현금결제 시 10% 할인', '서울 영등포구 문래동1가 39 센터플러스 1116호', NULL, @base2 + 17, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'etc-pactory');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'culture-awesome-coin-norae-hongdae', '어썸코인노래연습장 홍대', '문화', 37.5526977, 126.9237049, '1인 1곡 무료 / 네이버 리뷰 이벤트: 2곡 무료 쿠폰 2장 / 단골 인증 이벤트(카톡 채널 추가 시): 언제나 1곡씩 무료', '서울 마포구 와우산로21길 4', NULL, @base2 + 18, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-awesome-coin-norae-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'culture-killingtime-ott-hongdae', '킬링타임오티티 홍대점', '문화', 37.5509902, 126.9208633, '평일 방문 시 2시간 결제하면 1시간 추가, 캔음료 서비스', '서울 마포구 잔다리로 20 3, 4층', NULL, @base2 + 19, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-killingtime-ott-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'culture-atelier-hosoo-hongdae', '반지공방 아뜰리에호수 홍대', '문화', 37.5567260, 126.9262750, '반지체험 1인 1만원 할인', '서울 마포구 어울마당로 147-1 동원빌딩 4층', NULL, @base2 + 20, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'culture-atelier-hosoo-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'care-hair-the-beau-hongdae', '헤어더뷰 홍대점', '의료/미용', 37.5532380, 126.9241979, '커트 20% 할인, 펌·염색 30% 할인', '서울 마포구 홍익로 4 아남빌딩 2층, 3층', NULL, @base2 + 21, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'care-hair-the-beau-hongdae');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'care-bay-heoseulgi-blanc', '베이바이허슬기 블랑점', '의료/미용', 37.5526977, 126.9237049, '펌·염색·탈색 이벤트가에서 10% 추가 할인, 케어 메뉴 추가 시 한 단계 무상 업그레이드, 담당 디자이너 지정 재방문 쿠폰, 커트 단품 20% 할인, 붙임머리 패키지 5% 할인. 네이버 예약 시 미지정 예약, 메모에 홍대 기재', '서울 마포구 와우산로21길 4 1, 2층', NULL, @base2 + 22, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'care-bay-heoseulgi-blanc');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'care-bay-heoseulgi-spa-bleach', '베이바이허슬기 스파앤블리치점', '의료/미용', 37.5559824, 126.9239304, '펌·염색·탈색 이벤트가에서 10% 추가 할인, 케어 메뉴 추가 시 한 단계 무상 업그레이드, 담당 디자이너 지정 재방문 쿠폰, 커트 단품 20% 할인, 붙임머리 패키지 5% 할인. 네이버 예약 시 미지정 예약, 메모에 홍대 기재', '서울 마포구 홍익로6길 42 3, 4, 5층', NULL, @base2 + 23, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'care-bay-heoseulgi-spa-bleach');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'etc-woojungim-flower', '이케바나 우정임플라워', '기타', 37.5496040, 126.9229157, '학생증 제시 시 5% 할인 / 스탬프: 1만 5천원당 도장 1개, 8개 모으면 2만원 상당 미니 꽃다발 증정(유효기간 없음). 할인과 스탬프는 한 결제에서 중복 불가', '서울 마포구 와우산로 57 1층', NULL, @base2 + 24, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'etc-woojungim-flower');

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'etc-root-hongdae', '루트 홍대', '기타', 37.5550261, 126.9220538, '매주 수요일, 기존 15% 할인 이벤트 참여 시 5% 추가 할인(총 20%)', '서울 마포구 양화로 140 지하 1층', NULL, @base2 + 25, '2026-12-31'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'etc-root-hongdae');

INSERT INTO partner_affiliations (partner_id, affiliation, benefit)
SELECT p.id, '미술대학', NULL FROM partners p
WHERE p.code IN ('food-geunamja-bokkeumbap', 'food-bistro-sarangbang', 'food-momimomi-hongdae', 'food-kimdukhu-gamjatang-hongdae',
                 'food-suji-and-pasta', 'food-myeonsikdang-hongik', 'food-samcheongdang-hongdae', 'cafe-kkuul-gwajajeom',
                 'cafe-beargels', 'cafe-acai-monkey', 'bar-jahwasang', 'bar-bako-jumak',
                 'bar-taroya', 'etc-magazine-land', 'etc-hana-hwabang', 'culture-rgbcube',
                 'etc-pactory', 'culture-awesome-coin-norae-hongdae', 'culture-killingtime-ott-hongdae', 'culture-atelier-hosoo-hongdae',
                 'care-hair-the-beau-hongdae', 'care-bay-heoseulgi-blanc', 'care-bay-heoseulgi-spa-bleach', 'etc-woojungim-flower',
                 'etc-root-hongdae')
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 이미 등록된 업체에 미술대학 소속과 전용 혜택 더하기(소속 종료일 2026-12-31)

-- 찜이로닭 상수본점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '메뉴당 음료 1개(제로콜라·제로사이다 중 택 1) 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-jjimirodak-sangsu'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 네코노유부
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '방문 식사: 인당 유부 1피스 제공, 제휴 학생 3인 이상 방문 시 인당 가라아게 1피스 제공 / 포장: 10% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'food-nekono-yubu'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 홍대 마포껍데기
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '전 메뉴 10% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'food-mapo-kkeopdegi'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 연어초밥
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '1인당 음료수 1개 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-yeoneo-chobap'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 고토히라 우동
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '전체 금액에서 10% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'food-kotohira-udon'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 꼬치꼬치 양꼬치
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '테이블당 음료수 1개 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-kkochikkochi-yangkkochi'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 샌디빌리지
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '10,000원 이상 주문 시 10% 할인(리뷰이벤트와 중복 가능)', '2026-12-31' FROM partners p
WHERE p.code = 'food-sandy-village'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 또보겠지 떡볶이집(깐따삐아점)
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '2인 떡볶이 주문 시 음료수 1개, 3인 주문 시 사리 1개(세트·차돌 사리 제외), 4인 주문 시 버터갈릭감자튀김 또는 달콤베이컨감자튀김 택 1 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-ttoboketji-kkantapia'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 또보겠지 떡볶이집(해피토스점)
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '2인 떡볶이 주문 시 음료수 1개, 3인 주문 시 사리 1개(세트·차돌 사리 제외), 4인 주문 시 버터갈릭감자튀김 또는 달콤베이컨감자튀김 택 1 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-ttoboketji-happytoast'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 45년의정부부대찌개 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '13시 이후 방문 시 2인당 음료 1개 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-jeongbu-45nyeon-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 소코아 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '1인 방문 시 음료 1잔(주스, 캔음료) / 2~4인 방문 시 사이드 택 1(샌드류 인원수별 커팅, 고로케류 2개, 어니언링 8피스, 에비후라이)', '2026-12-31' FROM partners p
WHERE p.code = 'food-socoa-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 아웃닭 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '테이블당 사이드나 음료 택 1 제공', '2026-12-31' FROM partners p
WHERE p.code = 'food-outdak-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 요거트월드 홍대점
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '전 메뉴 15% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'cafe-yogurtworld-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 밀가루 인쇄소
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '전 메뉴 10% 할인, 2만원 이상 구매 시 케이크 1조각 증정, 홀케이크 주문 시 20% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'cafe-milgaru-inswaeso'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 하르카쿠키
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '전 메뉴 10% 할인, 5% 적립', '2026-12-31' FROM partners p
WHERE p.code = 'cafe-harka-cookie'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 영보스
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '개인주문: 음료 1잔당 쿠폰 1개 적립(5개 아메리카노 무료, 10개 영화쿠폰) / 단체주문: 2만원 이상 결제 2번 또는 3만원 이상 결제 시 영화쿠폰 제공', '2026-12-31' FROM partners p
WHERE p.code = 'cafe-yeongbos'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- 별헤는잔
INSERT INTO partner_affiliations (partner_id, affiliation, benefit, ends_on)
SELECT p.id, '미술대학', '전체 금액에서 10% 할인', '2026-12-31' FROM partners p
WHERE p.code = 'bar-byeolhaeneun-jan'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '미술대학');

-- ============================================
-- 경영대학 학생회 BE:st 26-2 제휴(@hongik_business). 제휴 기간 2026-09-01 ~ 2026-12-14, 대상은 경영대학 재·휴학생, 학생증 제시.
-- - 티하오 홍대입구점: 새 업체. ends_on 2026-12-14 — 12-15 부터 지도에서 자동으로 빠진다.
-- - T12: 같은 혜택으로 이미 등록돼 있다(bar-t12, 경영대학). 새로 넣지 않는다.
-- - 기존 경영대학 제휴 전체: 소속 종료일 2026-12-14.
-- ============================================

SET @base3 := (SELECT COALESCE(MAX(sort_order), 0) FROM partners);

INSERT INTO partners (code, name, category, latitude, longitude, benefit, address, hours, sort_order, ends_on)
SELECT 'cafe-tihao-hongdae', '티하오 홍대입구점', '카페', 37.5535057, 126.9238198, '전 메뉴 10% 할인', '서울 마포구 홍익로 10 B147호', NULL, @base3 + 1, '2026-12-14'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM partners WHERE code = 'cafe-tihao-hongdae');

INSERT INTO partner_affiliations (partner_id, affiliation, benefit)
SELECT p.id, '경영대학', NULL FROM partners p
WHERE p.code = 'cafe-tihao-hongdae'
  AND NOT EXISTS (SELECT 1 FROM partner_affiliations a WHERE a.partner_id = p.id AND a.affiliation = '경영대학');

-- 경영대학 제휴는 전부 12/14 까지(운영자 확인, 2026-10-13). 이미 등록된 경영대학 소속 전체에 종료일을 넣는다 —
-- 12-15 부터 경영대학 소속만 빠지고, 다른 소속 제휴가 없는 업체는 지도에서도 빠진다. 이미 종료일이 있는 행은 건드리지 않는다.
UPDATE partner_affiliations
SET ends_on = '2026-12-14'
WHERE affiliation = '경영대학' AND ends_on IS NULL;

-- 카페·음식점·주점 혜택 문구의 '품목'을 '메뉴'로 통일(운영자 요청, 2026-10-14). 이미 등록된 문구도 고친다(예: 써브웨이 상수점 경제학부 혜택).
UPDATE partners SET benefit = REPLACE(benefit, '품목', '메뉴')
WHERE category IN ('카페', '음식', '주점') AND benefit LIKE '%품목%';
UPDATE partner_affiliations a JOIN partners p ON p.id = a.partner_id
SET a.benefit = REPLACE(a.benefit, '품목', '메뉴')
WHERE p.category IN ('카페', '음식', '주점') AND a.benefit LIKE '%품목%';

-- 확인용
-- SELECT code, name, category, latitude, longitude, hours, ends_on FROM partners ORDER BY sort_order DESC LIMIT 34;
-- SELECT p.name, a.affiliation, a.benefit FROM partner_affiliations a JOIN partners p ON p.id = a.partner_id WHERE a.affiliation = '미술대학';
