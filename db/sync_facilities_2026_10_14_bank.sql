-- 2026-10-14 편의시설 종류 추가: 은행·ATM
-- 선행: 앱 OTA(모르는 시설 종류를 지도 데이터에서 거르는 버전, hongikon-fe)와 이 브랜치의 서버 배포(MapDataRules.FACILITY_KINDS).
--   옛 앱은 모르는 종류가 검색 결과에 걸리면 오류가 나므로 OTA 가 먼저다. 서버 배포는 관리자 화면에서 이 종류를 고르려면 필요하다(지도 데이터 읽기에는 필요 없음).
-- 다시 돌려도 안전하다(같은 값으로 UPDATE).
SET NAMES utf8mb4;

-- 신한은행 홍익대지점은 이미 '학생처'(앱 화면 '행정·지원') 항목으로 있다(hi-r-1f-office-04, 홍문관 R동 1층).
-- 새로 넣지 않고 종류를 은행·ATM 으로 옮기고, ATM 운영시간을 더한다(홍익대 편의시설 페이지, docs/campus-facilities-research.md 2.1).
UPDATE campus_facilities
SET kind = '은행·ATM',
    note = '신한은행 홍익대학교 지점 · 창구 평일 09:00~16:00 · ATM 07:00~23:30 · 02-6023-3950',
    updated_at = NOW(6)
WHERE code = 'hi-r-1f-office-04';

-- 확인: 1줄이어야 한다.
SELECT code, kind, floor, note FROM campus_facilities WHERE kind = '은행·ATM';
