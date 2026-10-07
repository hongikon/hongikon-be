-- ============================================
-- cafeteria_menus: 학식 메뉴 — 2026-10-07 (feat/cafeteria-menus)
-- 홍익대 홈페이지(서울캠퍼스 식당 페이지가 쓰는 JSON)에서 이번 주 메뉴를 가져와 저장한다(CafeteriaMenuFetchJob).
-- 앱은 GET /cafeteria/menus, /cafeteria/menus/week 로 이 테이블만 읽는다(학교 서버를 직접 부르지 않는다).
-- 코드: cafeteria.CafeteriaMenu (필드 단위 대조 완료).
--
-- ■ restaurant_code: dorm2-student(제2기숙사 학생식당) / mh-staff(교직원식당) — cafeteria.CafeteriaRestaurants.
-- ■ meal: 아침 / 점심 / 점심A / 점심B / 저녁.
-- ■ items: 메뉴 줄을 줄바꿈(\n)으로 이은 것(HTML 엔티티 풀고 앞뒤 공백 제거).
-- ■ closed: 메뉴 대신 '한글날', '대체공휴일 운영X', '휴무' 같은 문구만 온 날(휴무). items 에는 그 문구가 그대로 남는다.
-- ■ fetched_at: 마지막으로 가져온 시각(UTC — 서버 기준). API 는 KST 로 바꿔 내려준다.
-- ■ 같은 (식당, 날짜, 끼니)는 한 행 — 다시 가져오면 items·closed·fetched_at 을 덮어쓴다.
-- ■ 개인정보 없음(공개 메뉴 텍스트뿐).
-- ■ ddl-auto=validate 라 배포 전에 실행해야 한다. 여러 번 실행해도 안전하다(없을 때만 만든다).
-- ============================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `cafeteria_menus` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `restaurant_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `menu_date` date NOT NULL,
  `meal` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `items` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `closed` tinyint(1) NOT NULL DEFAULT 0,
  `fetched_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cafeteria_menus_restaurant_date_meal` (`restaurant_code`, `menu_date`, `meal`),
  KEY `idx_cafeteria_menus_menu_date` (`menu_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인: SHOW CREATE TABLE cafeteria_menus;
