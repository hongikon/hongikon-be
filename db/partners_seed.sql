-- 자동 생성: scripts/generate-partners-seed.mjs
-- 원본: src/constants/partners.ts (119개 항목)
-- 생성 시각: 2026-09-30T01:29:44.004Z

SET NAMES utf8mb4;
START TRANSACTION;

-- cafe-sunny-house
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('써니하우스', '카페', 37.5458493, 126.9224087, '학기 중 전 메뉴 10% 할인', '서울 마포구 와우산로 1길 8 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- cafe-dotti-rotti
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('도티로티', '카페', 37.5564525, 126.9253639, '총 금액 10% 할인', '서울 마포구 어울마당로 136-9 B동 지하 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- cafe-pelican
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('펠리칸 카페', '카페', 37.5474972, 126.9224553, '전체 메뉴 10% 할인', '서울 마포구 독막로 14길 27', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- cafe-bayerischer
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('바이어리셔', '카페', 37.5487676, 126.9152526, '전 메뉴 10% 할인', '서울 마포구 독막로 15길 24, 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- cafe-flan
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('플랑', '카페', 37.5485704, 126.9222782, '전 제품 10% 할인', '서울 마포구 와우산로 11길 9-8 102호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- cafe-weekly-bagel
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('위클리베이글', '카페', 37.5537619785992, 126.923693794834, '전 메뉴 10% 할인, 20만 원 이상 20% 할인', '서울 마포구 홍익로 10 101동 지층 B111호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-matjip
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('맷집', '주점', 37.5494066, 126.9215998, '총 금액 10% 할인', '서울 마포구 와우산로 17길 19-17 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-ilil-sujak
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('일일수작', '주점', 37.548958, 126.9192653, '테이블 당 금액 5% 할인, 과팅 진행 시 10% 할인', '서울 마포구 독막로 7길 26 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-gyepan-night
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('계판의 밤', '주점', 37.5491545, 126.9190471, '테이블 금액의 5% 할인, 과팅 진행 시 10% 할인', '서울 마포구 독막로 7길 27 1층 2호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-neurin-maeul
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('느린마을 양조장 홍대점', '주점', 37.5540178, 126.921832, '총 금액 10% 할인(토요일 제외)', '서울 마포구 홍익로 5안길 14 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-eori-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('어리 홍대', '주점', 37.5561326, 126.926443, '총 금액 10% 할인', '서울 마포구 와우산로 29길 69 지하1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-eori-yeonnam
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('어리 연남', '주점', 37.5628318, 126.9246555, '총 금액 10% 할인', '서울 마포구 동교로 257 지하1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- bar-nakwon
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('낙원', '주점', 37.5493802, 126.9213852, '메인메뉴 1개 제공', NULL, NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-kimdukhu-gopchangjo
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('김덕후의 곱창조 3호점', '음식', 37.553188, 126.9207831, '총 금액 10% 할인', '서울 마포구 양화로 16길 21', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-casa-pomodoro
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('까사 포모도로', '음식', 37.5581588, 126.920247, '평일 점심 방문시 1인 음료 1캔 무료, 평일 저녁/주말 방문 시 와인 1잔 무료', '서울 마포구 월드컵북로 6길 24-7 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-isu-jjukkumi
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('이수쭈꾸미', '음식', 37.5526328, 126.9220362, '주류, 사이드류 중 5,000원 내 할인', '서울 마포구 와우산로 21길 36-6 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-jeju-special
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('제주특별집', '음식', 37.5522325, 126.9212075, '소주 1,000원, 맥주 2,000원 할인', '서울 마포구 잔다리로 6길 36 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-fresh-engineered
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('프레쉬 엔지니어드', '음식', 37.5530588, 126.9264083, '샐러드 주문 시 음료 1개 무료', '서울 마포구 와우산로 116-1 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-subway-sangsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('서브웨이 상수점', '음식', 37.5483177, 126.923203, '학기 중 단품/세트 10% 할인', '서울 마포구 와우산로 44 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL),
  (@pid, '경제학부', '샌드위치·세트 전 품목 10% 할인(사이드 메뉴만 구매 시 할인 미적용)');

-- food-eggsum
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('에그섬', '음식', 37.551465, 126.9240704, '전 메뉴 10% 할인', '서울 마포구 와우산로 18길 30 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-el-carnitas
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('엘까르니따스', '음식', 37.561729, 126.9255662, '전 메뉴 10% 할인 (주류 제외)', '서울 마포구 동교로 38길 27-5 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-boseung-seogyo
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('보승회관 서교점', '음식', 37.5511743, 126.9222674, '11:00~14:00 - 음식 10% 할인, 17:00~20:00 - 전체 10% 할인', '서울 마포구 서교동 363-22 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-crepas
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('크레파스', '음식', 37.5489876, 126.9214919, '전체 금액의 10% 할인 (런치 스페셜 제외)', '서울 마포구 와우산로 15길 37 지1층 좌측', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- food-back-door
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('백도어', '음식', 37.5510244350131, 126.924188634391, '점심(11시~15시) 중 전 메뉴 10% 할인', '서울 마포구 와우산로22길 34', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-kenji-style
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('켄지스타일', '의료/미용', 37.5522583, 126.9221208, '펌, 염색/탈색 40% 할인, 커트 다운펌 10% 할인 등', '서울 마포구 서교동 364-26 5층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-artatti-hapjeong
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('아트아띠 합정점', '의료/미용', 37.5470888, 126.9194391, '밝은금발 탈색 3회 패치키 180,000원, 블랙빼기 무제한 패키지 200,000원, 탈색 시 염색 추가 +60,000원, 올라플렉스 케어 +40,000원, (전 시술 메뉴 기장 추가 X), 뿌리탈색 90,000원, 탈색 1회 80,000원, 뿌리염색 60,000원, 전체염색 80,000원, 탱글클리닉 120,000원', '서울 마포구 어울마당로 19 후문2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-supul
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('수푸울', '의료/미용', 37.548826, 126.9222747, '전 제품 15% 할인', '서울 마포구 상수동 93-104 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-vogue-hair-sangsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('보그헤어 상수역점', '의료/미용', 37.5483177, 126.923203, '첫 방문시, 40%(컷트 10%)할인, 재방문시, 30%(컷트 10%)할인, 멤버쉽 특별 혜택 제공', '서울 마포구 와우산로 44 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-best-checkup-guro
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('베스트 검진센터 구로점', '의료/미용', 37.4998962, 126.884113, '국가지원 검진 및 청춘블루 검진', '서울 구로구 구로중앙로 134', NULL, NULL, NULL, '병원', NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-best-checkup-gangnam
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('베스트 검진센터 강남점', '의료/미용', 37.5171047, 127.0394015, '국가지원 검진 및 청춘블루 검진', '서울 강남구 학동로 53길 3-2', NULL, NULL, NULL, '병원', NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- care-safedoc
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('세이프닥', '의료/미용', 37.552473, 126.9229842, NULL, '서울 마포구 와우산로 21길 20', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-pump-arcade
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('펌프 아케이드', '문화', 37.552473, 126.9229842, '평일 28% 할인', '서울 마포구 와우산로 21길 20', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-hide-and-seek
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('하이드 앤 시크', '문화', 37.5581588, 126.920247, '평일, 주말 10% 할인 등', '서울 마포구 월드컵북로 6길 24-7 SANTOH 빌딩 지하 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-hero-boardgame
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('히어로 보드게임 카페', '문화', 37.5520935, 126.9218362, '평일 한정 무제한 이용료 1,000원 할인, 탄산음료 무제한 리필, 2인 이상 방문 시 커피 한 잔 무제한 리필', '서울 마포구 와우산로 21길 31-10 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-megabox
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('메가박스', '문화', 37.5560662, 126.9220934, '영화 1만원 관람 쿠폰, 콤보 3,000원 할인. 아래 버튼으로 이벤트 페이지에 들어가 쿠폰을 먼저 발급받아야 한다.', '서울 마포구 양화로 147 아일렉스 7층', NULL, NULL, NULL, NULL, '쿠폰 발급 페이지 열기', 'https://m.megabox.co.kr/event/detail?eventNo=19954');
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-holiday-inn-express
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('홀리데이인 익스프레스 홍대', '문화', 37.55756143237883, 126.92674082033301, '전용 링크로 예약 시 객실 20% 할인, 국내 및 전 세계 IHG 계열사 호텔 할인 가능', '서울 마포구 양화로 188 (AK 플라자 7층)', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-lucy-lounge
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('루시라운지 홍대 1호점', '문화', 37.5517546, 126.9231886, '이용요금 20% 할인, 10명까지 추가 인원 요금 면제, 보증금(8만원) 면제', '서울 마포구 서교동 258-30 6층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-meta-comedy
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('메타코미디 클럽', '문화', 37.5512727, 126.9235667, '공연 티켓 20% 할인, 방문 시 하이볼 1잔 증정', '서울 마포구 와우산로 76-1', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-table-a-zoo
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('테이블에이 실내동물원', '문화', 37.5540566, 126.9295168, '입장 티켓 18,000원 → 12,000원', '서울 마포구 와우산로 146 B1-5층, 옆 건물 1-2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-fitness-m
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('휘트니스 엠', '기타', 37.547371, 126.9248081, NULL, '서울 마포구 상수동 157-1', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-gosu-driving
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('고수의 운전면허', '기타', 37.5989961, 126.9145739, NULL, '서울 은평구 은평로 59 가동 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-chloe-pilates
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('끌로에 필라테스', '기타', 37.5479183, 126.9235728, '그룹 레슨 10% 할인, 개인 레슨 10% 할인', '서울 마포구 독막로 91 동호빌딩 2, 3층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-vintage-urban
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('빈티지어반', '기타', 37.5524654, 126.9227265, '총 금액 20% 할인', '서울 마포구 와우산로 21길 24 지하 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-optic-life-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('옵틱라이프 홍대점', '기타', 37.5550314, 126.9237131, '제품 30% 할인', '서울 마포구 와우산로23길 50, 3층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-optic-life-seongsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('옵틱라이프 성수점', '기타', 37.5466182, 127.0546124, '제품 30% 할인', '서울 성동구 아차산로7길 15, 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-optic-life-gangnam
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('옵틱라이프 강남점', '기타', 37.4952167, 127.0313495, '제품 30% 할인', '서울 강남구 강남대로 78길 24, 4층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-optic-life-banghwa
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('옵틱라이프 방화점', '기타', 37.5649639, 126.8114457, '제품 30% 할인', '서울 강서구 방화동로 47, 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-royal-gym-1
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('로얄짐 1호점', '기타', 37.5517051, 126.91634, '3개월 150,000원, 6개월 222,000원', '서울 마포구 양화로 77 지하1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-royal-gym-2
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('로얄짐 2호점', '기타', 37.5478261, 126.9177948, '3개월 150,000원, 6개월 222,000원', '서울 마포구 독막로 42 지하1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-royal-gym-3
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('로얄짐 3호점', '기타', 37.5564029, 126.9159681, '3개월 150,000원, 6개월 222,000원', '서울 마포구 월드컵북로5길 54 1, 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-sidiz-hapjeong
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('시디즈 합정', '기타', 37.5513305, 126.9170757, '제품 13%~20% 할인', '서울 마포구 양화로 78 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- etc-geunbon-gym
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('근본짐', '기타', 37.5557792, 126.9203998, '정가 99,000원 → 77,000원', '서울 마포구 월드컵북로 9-1 4층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- edu-doodream-music
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('두드림 실용 음악 학원', '교육', 37.5555485, 126.9183198, '1개월권 2만원 할인, 드럼 스틱 제공', '서울 마포구 동교로 147, 4층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- edu-dear-dance
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('디얼 댄스', '교육', 37.5484317, 126.9248641, '1회 체험 50% 할인(15,000원)', '서울 마포구 독막로 19길 19, 4층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- edu-chosim-studycafe
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('초심 스터디카페', '교육', 37.5483517, 126.9205912, '계좌 이체 시 전 금액 10% 할인', '서울 마포구 독막로 67-13 2,3층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '총학생회', NULL);

-- culture-la-billiards
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('LA당구장', '문화', 37.5503273207971, 126.920740166452, '테이블당 음료 2개 서비스', '서울 마포구 어울마당로 55-4 서교빌딩 4층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL);

-- food-mapo-kkeopdegi
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('홍대 마포껍데기', '음식', 37.553647641358, 126.922585518447, '공대생 인증시 10% 할인', '서울 마포구 어울마당로 100-8 1, 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL);

-- food-jejejip
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('제제집', '음식', 37.5492805976055, 126.921634930985, '1인 1메뉴 주문시 2인당 음료수 or 공깃밥 1개', '서울 마포구 와우산로15길 30', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '기숙사', '2인 방문 시 음료수 1캔 증정, 3인 이상 방문 시 된장찌개 1그릇 증정');

-- food-oilnae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('오일내', '음식', 37.5513852765591, 126.921555321059, '테이블당 껍데기 or 비빔면 서비스', '서울 마포구 어울마당로 70 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '캠퍼스자율전공(서울)', '테이블당 치즈계란찜 or 된장찌개 or 김치찌개 or 껍데기 중 택 1');

-- food-yeoneo-chobap
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('연어초밥', '음식', 37.5482787, 126.9223832, '인당 음료수 1개 서비스', '서울 마포구 와우산로 39-14 지하 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '경영대학', NULL),
  (@pid, '캠퍼스자율전공(서울)', '테이블당 음료 1개 제공'),
  (@pid, '기숙사', NULL);

-- food-ttoboketji-kkantapia
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('또보겠지 떡볶이집(깐따삐아점)', '음식', 37.5528099161396, 126.922482495511, '2인 주문시 음료 1잔 3인 주문시 사리 1개(세트, 차돌 제외) 4인 주문시 버갈튀 또는 달콤베이컨감튀', '서울 마포구 와우산로21길 28-12', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '경영대학', '2인 주문시 음료 1잔 3인 주문시 사리 1개(세트, 차돌 제외) 4인 주문시 버갈튀 또는 달콤베이컨감튀 (확장이전이벤트로 2만원 이상 구매 시 치즈떡 서비스 추가 제공)');

-- food-ttoboketji-happytoast
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('또보겠지 떡볶이집(해피토스점)', '음식', 37.5521064426225, 126.921160400094, '2인 주문시 음료 1잔 3인 주문시 사리 1개(세트, 차돌 제외) 4인 주문시 버갈튀 또는 달콤베이컨감튀', '서울 마포구 잔다리로6길 34-5 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '경영대학', NULL);

-- food-ttoboketji-smileboy
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('또보겠지 떡볶이집(스마일보이점)', '음식', 37.5549743934334, 126.929243675616, '2인 주문시 음료 1잔 3인 주문시 사리 1개(세트, 차돌 제외) 4인 주문시 버갈튀 또는 달콤베이컨감튀', '서울 마포구 와우산로29길 14-8 101호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL);

-- food-socoa-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('소코아 홍대점', '음식', 37.548639, 126.920953, '1인 음료 1잔 / 2인 이상 사이드 택1', '서울 마포구 와우산로 15길 49 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '문과대학', NULL),
  (@pid, '경영대학', NULL),
  (@pid, '기숙사', NULL);

-- food-outdak-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('아웃닭 홍대점', '음식', 37.5500899621416, 126.921903530033, '2인당 1마리 주문시 테이블당 사이드 택1 제공', '서울 마포구 와우산로 17길 19 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL),
  (@pid, '기숙사', '한 테이블 당 사이드메뉴 1개 제공(택1)');

-- bar-chuntown-hongdae-1
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('춘타운 홍대점 1호점', '주점', 37.5508919345589, 126.922030684508, '2만원 이상 주문시 사이드 서비스', '서울 마포구 잔다리로 10, 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL);

-- bar-nas-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('나스', '주점', 37.5511310178553, 126.922652400213, '테이블당 3만 원 이상 주문 시 사이드 8,000원 서비스', '서울 마포구 와우산로 19길 9 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '공과대학', NULL);

-- culture-keyescape
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('키이스케이프', '문화', 37.548961893308, 126.920927426796, '인당 5,000원 페이백(이용 후 인스타그램 스토리 업로드 또는 영수증 리뷰 작성 → 직원 확인 시 혜택 적용)', '서울 마포구 어울마당로 44-1 라곰마빌딩 지하1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '문과대학', NULL);

-- food-jjimirodak-sangsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('찜이로닭 상수본점', '음식', 37.5481634403528, 126.92216581335, '주문 메뉴당 캔음료 1개 서비스 제공(2026년 6월 1일 ~ 2026년 12월 31일)', '서울 마포구 와우산로 39-21 지1층 좌측호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '문과대학', NULL);

-- cafe-yeongbos
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('영보스', '카페', 37.5498069, 126.924027, '전메뉴 10% 할인(음료만 가능)', '서울 마포구 독막로19길 42-18 지층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- cafe-harka-cookie
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('하르카쿠키', '카페', 37.5486521, 126.9219486, '전메뉴 10% 할인', '서울 마포구 독막로15길 19 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL),
  (@pid, '기숙사', NULL);

-- bar-t12
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('T12', '주점', 37.5511735, 126.9222841, '2잔 이상 주문 시 1만원 이하 칵테일 1잔 제공', '서울특별시 마포구 와우산로19길 15 지하', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- bar-aengchun
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('앵춘', '주점', 37.5479485, 126.9224248, '10% 할인(일~목요일 오후 7시 이전 방문 시)', '서울 마포구 독막로 81', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-hapjeong-naengjanggo
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('합정 냉장고', '음식', 37.54868, 126.920092, '고기 2인분 이상 주문 시 1인분 추가 제공(테이블당 1회)', '서울 마포구 양화로6길 99-10 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', '고기 2인분 이상 주문 시 테이블당 회 1인분 추가 제공(네이버 리뷰 작성 시)'),
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-nekono-yubu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('네코노유부', '음식', 37.548244, 126.92133, '홀 - 1인 1메뉴 기준 유부 1pcs 제공(멘치카츠 유부 제외) / 포장 - 평일 방문 포장 시 20% 할인', '서울 마포구 와우산로13길 49-11 반지층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', '홀 - 1인 1메뉴 기준 유부 1pcs 제공(멘치카츠 유부 제외), 3인 이상 방문 시 인당 가라아게 1pcs 추가 제공 / 포장 - 평일 방문 포장 시 20% 할인'),
  (@pid, '캠퍼스자율전공(서울)', '1인 1메뉴 또는 유부 4피스 주문 시 유부 1피스 제공(멘치카츠 유부 제외), 평일 포장 시 20% 할인'),
  (@pid, '기숙사', '방문포장 주문 시 결제 금액의 20% 할인 / 매장 식사 - 1인 1메뉴 주문 시 인당 원하는 유부 1pcs 제공, 제휴학생 3인 이상 방문 시 인당 가라아게 1pcs 추가 제공');

-- food-sandy-village
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('샌디빌리지', '음식', 37.547178, 126.922674, '1만 원 이상 구매 시 10% 할인', '서울 마포구 와우산로7길 6 1층 101호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL),
  (@pid, '기숙사', NULL);

-- food-suyo-chicken
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('수요치킨', '음식', 37.548062, 126.922792, '전메뉴 20% 할인', '서울 마포구 와우산로 39 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-hongdae-mulgalbi
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('홍대물갈비 홍대본점', '음식', 37.552912, 126.923026, '2인 이상 주문 시 왕새우튀김 2마리 또는 버터갈릭 감자튀김 중 택 1', '서울 마포구 홍익로 3-30 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-jincheong-yujeom
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('진청유점', '음식', 37.562275, 126.926294, '전메뉴 20% 할인', '서울 마포구 동교로46길 27 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-pujutgan-salon
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('푸줏간살롱', '음식', 37.5479355, 126.9221025, '전메뉴 10% 할인(점심특선 제외) + 고기구이 메뉴 주문 시 볶음밥 추가 제공', '서울 마포구 독막로 77 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-sinmigyeong-hongdae-dakgalbi
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('신미경홍대닭갈비', '음식', 37.5520826, 126.9218397, '리뷰 작성 시 전메뉴 10% 할인', '서울 마포구 와우산로21길 31-10 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-tacoeat-sangsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('타코잇 상수역점', '음식', 37.5484553, 126.9208686, '콤보 주문 시 피코 데 가요 + 소스 1종 제공 or 2인 세트 주문 시 타코 1pcs 제공 or 생맥 한 잔 주문 시 한 잔 추가 제공(1회 한정) 중 택 1', '서울 마포구 와우산로13길 49 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-sinlungpu-malatang
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('신룽푸마라탕', '음식', 37.553762, 126.9236938, '전메뉴 10% 할인', '서울 마포구 홍익로 10 101동 114~115호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '경영대학', NULL);

-- food-jeongbu-45nyeon-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('45년의정부부대찌개 홍대점', '음식', 37.5479899954542, 126.921210792783, '2인당 음료 1개 제공', '서울 마포구 상수동 316-8 연신B/D 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-genroku-udon-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('겐로쿠우동 홍대점', '음식', 37.5487545124007, 126.920300382188, '2인 이하: 1인당 이나리(유부초밥) or 고기어묵만두, 4인 이상: 고기어묵만두 or 타코야끼', '서울 마포구 서교동 402-18', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-motenatsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('모테나츠', '음식', 37.5489083934153, 126.920019799332, '음료 1개 or 한입 맥주 중 택 1', '서울 마포구 서교동 402-13 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-sangsu-jutaek
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('상수주택', '음식', 37.5486474124481, 126.921962182437, '학생증 제시 시 10% 할인, 쿠폰 제시 시 20% 할인(1회 제한, 자율전공학생회실에서 수령)', '서울 마포구 상수동 311-1 2층 상수주택', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-smashboy
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('스매쉬보이', '음식', 37.5489888494623, 126.921491971349, '버거+음료 주문시 1,000원 할인', '서울 마포구 서교동 411-9 1층 스매쉬보이', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-yunbanjang-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('원조한우곱도리탕 윤반장 홍대점', '음식', 37.5492164577926, 126.92247093994, '점심 - 메인메뉴 주문시 공기밥 제공 + 우동사리 or 당면서리 or 음료 중 택 1 / 저녁 - 우동사리/콘치즈/주먹밥/공기밥/음료 중 택 1', '서울 마포구 상수동 92-2 지하1층 우측', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL),
  (@pid, '기숙사', NULL);

-- food-kanda-soba
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('칸다소바', '음식', 37.5492898464803, 126.922680670023, '2인 기준 교자 or 음료 서비스 제공(평일에만 적용)', '서울 마포구 상수동 91-3', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-kotohira-udon
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('고토히라 우동', '음식', 37.5550790657471, 126.929204305697, '2인 테이블당 치쿠와튀김 1개 제공', '서울 마포구 서교동 327-20 지하1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-kkochikkochi-yangkkochi
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('꼬치꼬치 양꼬치', '음식', 37.5545304021785, 126.92294268134, '2인: 인당 음료 1개씩 제공, 3인 이상: 계란볶음밥 or 물만두 중 택 1', '서울 마포구 서교동 345-23 2-3층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-thepizzaboys-hongikuniv
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('더피자보이즈 홍대입구역점', '음식', 37.5557338744195, 126.92634638809, '3인 이상 - 일반감자튀김 or 치즈윗', '서울 마포구 서교동 332-33 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-taomalatang-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('타오마라탕 홍대점', '음식', 37.5524804477603, 126.922385737882, '2인당 음료 1개 제공', '서울 마포구 서교동 358-38 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-taomalatang-sinchon
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('타오마라탕 신촌점', '음식', 37.5570473388332, 126.935906636005, '2인당 음료 1개 제공', '서울 서대문구 연세로5나길 6 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-taomalatang-hapjeong
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('타오마라탕 합정점', '음식', 37.5492506318267, 126.915295517355, '2인당 음료 1개 제공', '서울 마포구 양화로6길 19 광명빌딩 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-taomalatang-nowon
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('타오마라탕 노원점', '음식', 37.6564840257668, 127.063004906422, '2인당 음료 1개 제공', '서울 노원구 상계로 71 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- food-marai-jangwon
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('마라이장원', '음식', 37.5484862624422, 126.920133413176, '전메뉴 10% 할인', '서울 마포구 서교동 402-22 1층 1호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- cafe-am9-coffee
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('AM9 coffee', '카페', 37.5479835725677, 126.925330803359, '아메리카노 제외 500원 할인 or 모든 음료 무료 사이즈업', '서울 마포구 상수동 93-1 1층 101호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- cafe-hooligan-coffee
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('훌리건 커피', '카페', 37.5557338744195, 126.92634638809, '평일 3시 이전 런치콤보 10% 할인, 3시 이후 음료(맥주 포함) 20% 할인, 3인 이상 방문 시 아이스크림 디저트 제공', '서울 마포구 서교동 332-28 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- cafe-yogurtworld-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('요거트월드 홍대점', '카페', 37.5525907369382, 126.923501017298, '전 메뉴 15% 할인', '서울 마포구 서교동 358-32 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- cafe-milgaru-inswaeso
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('밀가루 인쇄소', '카페', 37.5484305543692, 126.924757414614, '음료 30% 할인, 디저트 20% 할인', '서울 마포구 상수동 93-74 지층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- bar-siseon-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('시선 홍대점', '주점', 37.5502713850633, 126.922666981571, '20,000원 이하 안주 제공(안주 39,900원 이상 + 주류 주문 시, 테이블 절반 이상이 자율전공 학생일 시, 금·토 제외)', '서울 마포구 서교동 407-4 1층, 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '캠퍼스자율전공(서울)', NULL);

-- etc-archi-lounge
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('아키라운지', '기타', 37.553419, 126.923362, '레이저 컷팅 작업 15분 이상 진행시 상시 15% 할인', '서울 마포구 홍익로 9-1 3층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '미술대학', NULL);

-- food-green-n-berry
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('그린앤베리', '음식', 37.5603182, 126.9159742, '포케 or 셀러드 메뉴 주문시 아이스아메리카노, 생토마토주스, 생바나나주스 중 택 1 제공', '서울 마포구 월드컵북로 73 1층 그린앤베리', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- food-cheongnyeon-chicken-seogyo
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('청년치킨 서교점', '음식', 37.5586175, 126.9126327, '[반마리] 콜라 500ml or 1,000원 할인 중 택 1 / [한마리] 콜라 500ml or 2,000원 할인 or 3,000원 상당 사이드 중 택 1', '서울 마포구 잔다리로 133 101호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- food-mapo-la-restaurant
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('마포라스토랑', '음식', 37.5487522, 126.9214001, '김 추가(3장) or 멘마 추가 or 차슈 추가(2장) or 베이컨 치즈밥 변경 중 택 1', '서울 마포구 와우산로11길 28 지1층 B03호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- food-sogeum-jemyeonso-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('소금제면소 홍대', '음식', 37.5502888, 126.9215063, '[1인] 음료 or 만두 2개 중 택 1 / [2인] 만두 4개 or 가라아게 중 택 1', '서울 마포구 와우산로17길 26 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- food-sneakers-burger-club
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('스니커즈 버거 클럽', '음식', 37.5502986, 126.9216382, '단품 주문시 세트메뉴 업그레이드(20시 이전 현장 방문 시에만 가능)', '서울 마포구 와우산로17길 24 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- food-afc-kebab
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('AFC Kebab', '음식', 37.5496248, 126.9228733, '메인메뉴 주문시 콜라 or 물 중 택 1, 3만 원 이상 주문시 Middle Size 케밥 제공', '서울 마포구 와우산로 57 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- food-doner-kebab
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('Doner Kebab', '음식', 37.5524518, 126.9227363, '(7,900원~18,900원: M 사이드 메뉴 1개 or 2pcs 사이드 메뉴 제공 / 19,000원~37,800원: L 사이드 메뉴 1개 or 음료 1개 제공 / 37,900원~: L 사이드 메뉴 2개 or 1인분 세트 메뉴로 업그레이드 or 음료 2캔 제공) or 전체 금액에서 10% 할인', '서울 마포구 와우산로21길 24 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- bar-sijangeul-yeoneun-saramdeul-hongdae
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('시장을여는사람들 홍대점', '주점', 37.550509, 126.9223121, '12% 할인 (주의사항 - 기숙사 카드키 필수 지참, 처음 들어왔을 때 같이 보여주기, 첫 입점부터 자리 지켜야 함, 중복X, 다양한 서비스 가능)', '서울 마포구 잔다리로 5-1 2층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- bar-byeolhaeneun-jan
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('별헤는잔', '주점', 37.5502077, 126.9232577, '총 금액의 10% 할인', '서울 마포구 와우산로 64 3층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- bar-pico
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('피코', '주점', 37.548965, 126.9199038, '총 금액의 10% 할인', '서울 마포구 독막로9길 14', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- bar-zigzagg
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('ZigZagg', '주점', 37.5518173, 126.9225215, '[4명 이하 방문시] 3,000원~4,000원 상당 무료 드링크 제공 / [5명 이상 방문시] 바틀 제공', '서울 마포구 와우산로21길 19-16 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- cafe-comoedoi
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('코모이도이', '카페', 37.5589328, 126.9117256, '전 음료 20% 할인, 디저트 10% 할인(1인 1음료 주문시 적용)', '서울 마포구 성미산로 31 1층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- cafe-rave-espresso-sangsu
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('레이브 에스프레소 바 상수', '카페', 37.5489195, 126.9237589, '전메뉴 300원 할인', '서울 마포구 독막로19길 43 1층 우측호', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- edu-ari-studycafe
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('아리 스터디 카페', '교육', 37.5458578, 126.9276904, '시간권 구매시 10시간 추가 증정(결제 시간 상관 X), 기간권 구매시 48시간 추가 증정 or 고정석 업그레이드 제공 (키오스크로 결제 → 기숙사 카드키 촬영 → 윗 번호로 사진 전송)', '서울 마포구 토정로 149 영재빌딩 2층 아리스터디카페', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

-- etc-lagom-pilates
INSERT INTO partners (name, category, latitude, longitude, benefit, address, road_address, hours, contact, map_icon, link_label, link_url)
VALUES ('라곰필라테스', '기타', 37.5458578, 126.9276904, '1:1 개인 레슨 - 8회(주 2회/4주) 528,000원, 16회(주 2회/8주) 1,056,000원 / 2:1 듀엣 레슨 - 8회(주 2회/4주) 320,000원, 16회(주 2회/8주) 576,000원', '서울 마포구 토정로 149 4층', NULL, NULL, NULL, NULL, NULL, NULL);
SET @pid = LAST_INSERT_ID();
INSERT INTO partner_affiliations (partner_id, affiliation, benefit) VALUES
  (@pid, '기숙사', NULL);

COMMIT;
