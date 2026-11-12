-- ============================================
-- 홍문관 1.5층 출입구(HI_R_1F5_ENTER) 삭제 — 2026-11-10 (사용자 요청)
-- 엑셀 층 정보·경로망 조사(v3)에 없는 층이고 어디인지 확인되지 않았다. 출입구 JSON 에서 그 항목만 뺀다.
-- 이미 없으면 아무것도 바꾸지 않는다(두 번 실행해도 된다). 경로망(path_nodes)·서버 길찾기는 이 출입구를 쓰지 않는다.
-- (sync_building_floors_2026_11_10.sql 과 따로 둔 까닭: 그 파일이 먼저 main 에 들어가 이미 실행됐을 수 있다.)
-- ============================================

UPDATE `buildings`
SET `entrances` = JSON_REMOVE(`entrances`, REPLACE(JSON_UNQUOTE(JSON_SEARCH(`entrances`, 'one', 'HI_R_1F5_ENTER', NULL, '$[*].label')), '.label', ''))
WHERE `code` = 'hongik_r' AND JSON_SEARCH(`entrances`, 'one', 'HI_R_1F5_ENTER', NULL, '$[*].label') IS NOT NULL;

-- 확인: SELECT JSON_EXTRACT(entrances, '$[*].label') FROM buildings WHERE code = 'hongik_r';  -- HI_R_1F5_ENTER 가 없어야 한다
