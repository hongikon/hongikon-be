-- 2026-10-14 편의시설 종류 추가: 은행·ATM, 휴게실
-- 선행: 앱 OTA(모르는 시설 종류를 지도 데이터에서 거르는 버전, hongikon-fe)와 이 브랜치의 서버 배포(MapDataRules.FACILITY_KINDS).
--   옛 앱은 모르는 종류가 검색 결과에 걸리면 오류가 나므로 OTA 가 먼저다. 서버 배포는 관리자 화면에서 이 종류를 고르려면 필요하다(지도 데이터 읽기에는 필요 없음).
-- 다시 돌려도 안전하다(INSERT 는 code 가 있으면 내용만 갱신, UPDATE 는 같은 값).
SET NAMES utf8mb4;

-- 은행·ATM: 신한은행 홍익대지점(홍문관 로비층 = 1층으로 표기, 공식 학생증 발급 페이지 "홍문관 1층").
-- 출처: 홍익대 편의시설 페이지(/kr/life/facility.do), 조사 문서 docs/campus-facilities-research.md 2.1
INSERT INTO campus_facilities (code, kind, building_id, floor, note, latitude, longitude, sort_order, created_at, updated_at)
VALUES ('hi-r-1f-bank', '은행·ATM', (SELECT id FROM buildings WHERE code = 'hongik_r'), 1,
        '신한은행 홍익대지점 · 창구 평일 09:00~16:00 · ATM 07:00~23:30 · 02-6023-3950', NULL, NULL,
        (SELECT next_order FROM (SELECT COALESCE(MAX(sort_order), 0) + 1 AS next_order FROM campus_facilities) t), NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE kind = VALUES(kind), building_id = VALUES(building_id), floor = VALUES(floor), note = VALUES(note), updated_at = NOW(6);

-- 휴게실: 남·여학생 휴게실은 잠자는 방(수면실)이 아니라 쉬는 방이라 따로 뺀다(10-14 요청).
UPDATE campus_facilities SET kind = '휴게실', updated_at = NOW(6) WHERE code IN ('hi-q-1f-nap-room', 'hi-d-b1f-nap-room');

-- 확인
SELECT code, kind, floor, note FROM campus_facilities WHERE kind IN ('은행·ATM', '휴게실', '수면실') ORDER BY kind, code;
