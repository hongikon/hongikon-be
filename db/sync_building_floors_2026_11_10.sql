-- ============================================
-- 건물별 층 정보(길찾기 층 다이얼) — 2026-11-10
-- 원본: 홍익대학교_건물_정보_amenity_locations_1109.xlsx 의 floors 시트(사용자 제공).
-- - buildings.floor_levels(새 열, JSON 층 목록)에 고를 수 있는 층을 그대로 적는다. 0 = 로비층 L.
--   floors·basement_floors 는 그 목록의 지상 최상층·지하 최하층(이 열을 모르는 예전 앱용).
-- - 홍문관 R동: 엑셀대로 B6~B2, L, 1~16층(B1 없음, L = 엑셀 '지하1층=로비층'). 앱·경로 조사는 L 을 0 으로 적는다.
-- - 인문사회관 A동: A동 n층 = B동 (n-2)층(B동 지하2층 = A동 1층, 지하1층 = A동 2층, 사용자 확인 11-10) — A동은 1~7층으로 두고
--   서버 길찾기(WalkRouter.sameLevel)가 A동 3층 이상은 B동, B동 지하층은 A동으로 안내한다.
-- - 제2·제3기숙사: 엑셀에 1·2층 줄이 두 번 나오지만 가장 높은 층·가장 낮은 지하층만 쓴다(사용자 확인).
-- - 제1기숙사(HI_DORM1)·운동장은 서버 지도에 없거나 층이 없어 넣지 않는다.
-- ■ ddl-auto=validate 라 배포 전에 실행한다. ALTER 는 한 번만(두 번 실행하면 '중복 열' 오류) — 먼저 SHOW COLUMNS 로 확인.
-- ============================================

ALTER TABLE `buildings` ADD COLUMN `floor_levels` json NULL AFTER `basement_floors`;

UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5, 6, 7]', `floors` = 7, `basement_floors` = NULL WHERE `code` = 'hongik_a'; -- 인문사회관 A동
UPDATE `buildings` SET `floor_levels` = '[-2, -1, 1, 2, 3, 4, 5]', `floors` = 5, `basement_floors` = 2 WHERE `code` = 'hongik_b'; -- 인문사회관 B동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]', `floors` = 10, `basement_floors` = 1 WHERE `code` = 'hongik_c'; -- 인문사회관 C동
UPDATE `buildings` SET `floor_levels` = '[-5, -4, -3, -2, -1, 1, 2]', `floors` = 2, `basement_floors` = 5 WHERE `code` = 'hongik_d'; -- 인문사회관 D동
-- HI_DORM1: 서버 지도에 없음 — 건너뜀
UPDATE `buildings` SET `floor_levels` = '[-4, -3, -2, -1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24]', `floors` = 24, `basement_floors` = 4 WHERE `code` = 'hongik_dorm2'; -- 제2기숙사
UPDATE `buildings` SET `floor_levels` = '[-3, -2, -1, 1, 2, 3, 4, 5]', `floors` = 5, `basement_floors` = 3 WHERE `code` = 'hongik_dorm3'; -- 제3기숙사
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]', `floors` = 10, `basement_floors` = 1 WHERE `code` = 'hongik_e'; -- 조형관
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5, 6]', `floors` = 6, `basement_floors` = NULL WHERE `code` = 'hongik_f'; -- 미술학관 F동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4, 5, 6]', `floors` = 6, `basement_floors` = 1 WHERE `code` = 'hongik_g'; -- 학생회관 G동
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5, 6]', `floors` = 6, `basement_floors` = NULL WHERE `code` = 'hongik_h'; -- 중앙도서관 H동
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5, 6]', `floors` = 6, `basement_floors` = NULL WHERE `code` = 'hongik_i'; -- 과학관 I동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4]', `floors` = 4, `basement_floors` = 1 WHERE `code` = 'hongik_j'; -- 제3공학관 J동
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5, 6]', `floors` = 6, `basement_floors` = NULL WHERE `code` = 'hongik_k'; -- 제1공학관 K동
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]', `floors` = 10, `basement_floors` = NULL WHERE `code` = 'hongik_l'; -- 와우관 L동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4, 5]', `floors` = 5, `basement_floors` = 1 WHERE `code` = 'hongik_gym'; -- 체육관
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]', `floors` = 16, `basement_floors` = 1 WHERE `code` = 'hongik_mh'; -- 문헌관 MH동
UPDATE `buildings` SET `floor_levels` = '[-2, -1, 1, 2, 3, 4, 5, 6, 7, 8]', `floors` = 8, `basement_floors` = 2 WHERE `code` = 'hongik_p'; -- 제2공학관 P동
UPDATE `buildings` SET `floor_levels` = '[-2, -1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]', `floors` = 11, `basement_floors` = 2 WHERE `code` = 'hongik_q'; -- 정보통신센터 Q동
UPDATE `buildings` SET `floor_levels` = '[-6, -5, -4, -3, -2, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]', `floors` = 16, `basement_floors` = 6 WHERE `code` = 'hongik_r'; -- 홍문관 R동
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3]', `floors` = 3, `basement_floors` = NULL WHERE `code` = 'hongik_s'; -- 강당 S동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]', `floors` = 10, `basement_floors` = 1 WHERE `code` = 'hongik_t'; -- 제4공학관 T동
UPDATE `buildings` SET `floor_levels` = '[-4, -3, -2, -1, 1, 2, 3, 4, 5, 6, 7]', `floors` = 7, `basement_floors` = 4 WHERE `code` = 'hongik_u'; -- 미술종합강의동 U동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2]', `floors` = 2, `basement_floors` = 1 WHERE `code` = 'hongik_z1'; -- 제1강의동 Z1동
UPDATE `buildings` SET `floor_levels` = '[1, 2, 3, 4, 5]', `floors` = 5, `basement_floors` = NULL WHERE `code` = 'hongik_z2'; -- 이천득관 Z2동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3]', `floors` = 3, `basement_floors` = 1 WHERE `code` = 'hongik_z3'; -- 제3강의동 Z3동
UPDATE `buildings` SET `floor_levels` = '[-1, 1, 2, 3, 4]', `floors` = 4, `basement_floors` = 1 WHERE `code` = 'hongik_z4'; -- 제4강의동 Z4동

-- 확인: SELECT code, floors, basement_floors, floor_levels FROM buildings ORDER BY sort_order, id;
