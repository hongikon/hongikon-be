-- ============================================
-- promo_visits: 홍보 링크(hongikon.com/app/{source}) 방문 수. 경로·날짜(KST)별 숫자 하나만 둔다.
-- IP·User-Agent·사용자 정보는 저장하지 않는다(개인정보 아님 — 처리방침 변경 불필요).
-- 엔티티 없이 PromoVisitService 가 SQL 로만 쓴다. 이 테이블이 없어도 서버는 뜨고 이동도 되며 숫자만 안 쌓인다.
-- ============================================

CREATE TABLE IF NOT EXISTS `promo_visits` (
  `source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `visit_date` date NOT NULL,
  `visits` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`source`, `visit_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
