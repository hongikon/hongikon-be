-- 2026-10-27 흡연구역 정리(운영진이 직접 다니며 좌표를 찍은 11곳)
-- 새로 9곳을 넣고, 이미 있던 2곳(B동·제2기숙사)은 같은 자리라 좌표·메모만 맞춘다.
-- 좌표는 건물 윤곽·중심과 대조함(10-27): 모두 적은 건물에서 15~62m. 문헌관 앞은 제1강의동 Z1 윤곽 안에 찍혔지만
-- 운영진 확인 결과 그 자리가 맞다. T동 주차장은 제4강의동 Z4 쪽(운영진 확인).
-- 층: 야외는 1층으로 둔다(기존 B동·기숙사와 같은 방식). K동 옥상·G동 여자휴게실 앞은 층을 몰라 NULL(앱에 '층 확인 중').
-- 다시 돌려도 안전하다(code 가 같으면 값만 덮어쓴다). 서버 배포는 필요 없다(종류 '흡연구역'은 이미 있다).
SET NAMES utf8mb4;

INSERT INTO campus_facilities (code, building_id, kind, floor, latitude, longitude, note, sort_order, created_at, updated_at)
SELECT v.code, b.id, '흡연구역', v.floor, v.lat, v.lng, v.note, v.sort_order, NOW(6), NOW(6)
FROM (
    SELECT 'hi-k-roof-smoking-01' AS code, 'hongik_k' AS bcode, NULL AS floor, 37.5520273 AS lat, 126.9264693 AS lng, 'K동 옥상 흡연구역 (야외)' AS note, 118 AS sort_order
    UNION ALL SELECT 'hi-k-1f-outdoor-smoking-01', 'hongik_k', 1, 37.5517456, 126.9257867, 'K동 앞 흡연구역 (야외)', 119
    UNION ALL SELECT 'hi-l-4f-smoking-01', 'hongik_l', 4, 37.5515302, 126.9265176, 'L동 4층 흡연구역', 120
    UNION ALL SELECT 'hi-u-1f-outdoor-smoking-01', 'hongik_u', 1, 37.5504081, 126.9264406, 'U동 적색 계단 흡연구역 (야외)', 121
    UNION ALL SELECT 'hi-g-xf-smoking-01', 'hongik_g', NULL, 37.5512667, 126.9264024, '학생회관 G동 여자휴게실 앞 흡연구역', 122
    UNION ALL SELECT 'hi-mh-1f-outdoor-smoking-01', 'hongik_mh', 1, 37.5504002, 126.9255511, '문헌관 MH동 앞 흡연구역 (야외)', 123
    UNION ALL SELECT 'hi-c-8f-terrace-smoking-01', 'hongik_c', 8, 37.5496788, 126.9260948, 'C동 8층 테라스 흡연구역', 124
    UNION ALL SELECT 'hi-c-1f-outdoor-smoking-01', 'hongik_c', 1, 37.5489905, 126.9262671, 'C동 후문 좌측 계단 위 흡연구역 (야외)', 125
    UNION ALL SELECT 'hi-t-1f-outdoor-smoking-01', 'hongik_t', 1, 37.5506207, 126.9247709, 'T동 주차장 흡연구역 (야외)', 126
) v
JOIN buildings b ON b.code = v.bcode
ON DUPLICATE KEY UPDATE
    building_id = VALUES(building_id),
    floor = VALUES(floor),
    latitude = VALUES(latitude),
    longitude = VALUES(longitude),
    note = VALUES(note),
    updated_at = NOW(6);

-- 기존 2곳: 같은 자리(운영진 확인). B동은 기존 좌표가 49m 어긋나 있어 새로 찍은 1층 테라스 좌표로 옮긴다.
UPDATE campus_facilities
SET latitude = 37.5501881, longitude = 126.9258488, note = 'B동 1층 테라스 흡연구역 (야외)', updated_at = NOW(6)
WHERE code = 'hi-b-1f-outdoor-smoking-01';
UPDATE campus_facilities
SET note = '기숙사 1층 후면 야외 흡연장', updated_at = NOW(6)
WHERE code = 'hi-dorm2-1f-outdoor-smoking-01';

-- 확인: 11줄이어야 한다.
SELECT f.code, b.name, f.floor, f.latitude, f.longitude, f.note
FROM campus_facilities f JOIN buildings b ON b.id = f.building_id
WHERE f.kind = '흡연구역' ORDER BY f.sort_order;
