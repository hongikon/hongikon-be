-- 2026-11-12 사용자 피드백 반영(물어보기 '교내 카페·편의점·식당·열람실' 답이 틀리거나 빠짐)
-- 1) 문 닫은 곳 2곳을 지운다: 홍문관 R동 2층 카페 '파브리카'(운영진 확인), 문헌관 MH동 16층 '교직원식당'(2026-10-31 운영 종료,
--    위탁운영 계약 만료). campus_facilities 에는 숨김(soft delete) 칸이 없고, 지금까지 시설을 뺄 때도 행을 지웠다
--    (sync_map_data_2026_10_06.sql). 전시(exhibitions.facility_code)는 외래키가 없고 없는 장소의 전시는 /map/data 에서 빠진다.
--    학식 메뉴 대응표(CafeteriaRestaurants 의 hi-mh-16f-restaurant)는 그대로 두어도 된다 — 앱은 지도에 있는 시설에만 메뉴를 붙인다.
-- 2) 빠진 곳 2곳을 넣는다: 제1공학관 K동 6층 열람실, 와우관 L동 4층 편의점. 이름·운영시간을 아직 모른다 —
--    지어내지 않고 메모에 '확인 필요'로 적는다(앱 지도와 물어보기 답에 그대로 보인다). 좌표는 다른 층 시설처럼 비워 둔다(건물 위치로 보인다).
-- 다시 돌려도 안전하다(DELETE 는 없으면 0줄, INSERT 는 code 가 같으면 값만 덮어쓴다).
-- 서버 배포 없이 지도·물어보기에 반영된다(지도 데이터 캐시 최대 5분). 종류 '열람실'·'편의점'은 이미 있다.
SET NAMES utf8mb4;

DELETE FROM campus_facilities
WHERE code IN (
    'hi-r-2f-cafe-01',      -- 홍문관 R동 2층 파브리카
    'hi-mh-16f-restaurant'  -- 문헌관 MH동 16층 교직원식당
);

INSERT INTO campus_facilities (code, building_id, kind, floor, latitude, longitude, note, sort_order, created_at, updated_at)
SELECT v.code, b.id, v.kind, v.floor, NULL, NULL, v.note, v.sort_order, NOW(6), NOW(6)
FROM (
    SELECT 'hi-k-6f-reading-room' AS code, 'hongik_k' AS bcode, '열람실' AS kind, 6 AS floor,
           '열람실 · 운영시간·이용 대상 확인 필요' AS note, 275 AS sort_order
    UNION ALL SELECT 'hi-l-4f-convenience-store', 'hongik_l', '편의점', 4,
           '편의점 · 이름·운영시간 확인 필요', 276
) v
JOIN buildings b ON b.code = v.bcode
ON DUPLICATE KEY UPDATE
    building_id = VALUES(building_id),
    kind = VALUES(kind),
    floor = VALUES(floor),
    note = VALUES(note),
    updated_at = NOW(6);

-- 확인 1: 0줄이어야 한다.
SELECT code, kind, note FROM campus_facilities WHERE code IN ('hi-r-2f-cafe-01', 'hi-mh-16f-restaurant');
-- 확인 2: 2줄(제1공학관 6층 열람실, 와우관 4층 편의점).
SELECT f.code, b.name, f.kind, f.floor, f.note
FROM campus_facilities f JOIN buildings b ON b.id = f.building_id
WHERE f.code IN ('hi-k-6f-reading-room', 'hi-l-4f-convenience-store');
