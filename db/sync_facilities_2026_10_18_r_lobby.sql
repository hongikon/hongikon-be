-- 2026-10-18 홍문관 R동 로비층(L) 시설 층 바로잡기
-- R동은 B1 과 1층 사이에 L층(로비층)이 따로 있다(운영자 확인). 아래 네 곳은 L층인데 floor = 1 로 들어가 앱에 1F 로 보였다.
-- 층 값 0 = 로비층 L(앱 표기 'L', 물어보기 답 '로비층(L층)'). code 는 앱이 참조하는 식별자라 그대로 둔다.
-- 선행: 앱 OTA(hongikon-fe formatFloor 0 → 'L'). 옛 번들은 0 을 'B0' 으로 그리므로 OTA 가 먼저다.
--   서버 배포(AskLiveData.floorLabel)는 물어보기 답의 층 표기에만 필요하다.
-- 다시 돌려도 안전하다(같은 값으로 UPDATE).
SET NAMES utf8mb4;

UPDATE campus_facilities
SET floor = 0,
    updated_at = NOW(6)
WHERE code IN (
    'hi-r-1f-certificate-kiosk', -- 증명서 출력기
    'hi-r-1f-cafe',              -- 카페나무
    'hi-r-1f-seminar-room',      -- 카페나무 세미나실
    'hi-r-1f-office-04'          -- 신한은행 홍익대학교 지점(은행·ATM)
);

-- 확인: 4줄, floor 모두 0 이어야 한다.
SELECT code, kind, floor, note FROM campus_facilities WHERE floor = 0;
