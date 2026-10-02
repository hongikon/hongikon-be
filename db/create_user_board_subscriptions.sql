-- ============================================
-- user_board_subscriptions: 유저별 게시판 구독 + 게시판별 알림 on/off.
-- source_id = 크롤러 BoardConfig.sourceId = news.source_id
--   (학과 게시판이면 학과명, 대학공지면 학사/장학/교수학습지원/학생상담/대학혁신지원사업/학생활동).
-- 새 소식 푸시 대상: 그 게시판 구독 + alert_enabled = TRUE + 그 카테고리를 끄지 않은 유저 (+ 키워드 일치 유저).
-- UserBoardSubscription.java 엔티티와 필드 단위 대조 완료.
-- ddl-auto=validate 환경이므로 배포 전 이 DDL을 RDS에 직접 실행해야 함 (빠뜨리면 서버가 기동하지 않음).
-- 여러 번 실행해도 안전함 (CREATE TABLE IF NOT EXISTS + INSERT IGNORE).
-- ============================================

CREATE TABLE IF NOT EXISTS `user_board_subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `source_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `alert_enabled` boolean NOT NULL DEFAULT TRUE,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_board_sub_user_source` (`user_id`,`source_id`),
  KEY `ix_board_sub_source_alert` (`source_id`,`alert_enabled`),
  CONSTRAINT `fk_board_sub_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------
-- 백필: 기존 학과 구독(user_departments)을 게시판 구독(알림 켬)으로 옮긴다.
-- departments.name = 크롤러 sourceId (db/seed_departments.sql 규칙, 34개 학과 게시판 전부 일치 확인).
-- 크롤러 게시판이 없는 학과(기초과학과·융합전공 등)는 구독 API가 400으로 거절하는 값이라 옮기지 않는다
-- — 아래 IN 목록은 CrawlerBoards.DEPARTMENT_BOARDS의 sourceId와 같아야 한다.
-- 대학공지(학사·장학 등)는 기존에 "카테고리를 끄지 않은 전원"에게 갔지만, 이제는 구독자에게만 간다(PR 본문 참고).
-- user_departments는 그대로 둔다(학과 API는 유지, 푸시에만 안 씀).
-- --------------------------------------------
INSERT IGNORE INTO `user_board_subscriptions` (`user_id`, `source_id`, `alert_enabled`, `created_at`, `updated_at`)
SELECT DISTINCT ud.`user_id`, d.`name`, TRUE, NOW(6), NOW(6)
FROM `user_departments` ud
JOIN `departments` d ON d.`id` = ud.`department_id`
WHERE d.`name` IN (
  '컴퓨터공학과', '전자전기공학부', '신소재공학전공', '화학공학전공', '산업데이터공학과',
  '기계시스템디자인공학과', '건설환경공학과', '건축학부', '도시학과', '경제학부', '경영학부',
  '영어영문학과', '독어독문학과', '불어불문학과', '국어국문학과', '법학부',
  '교육학과', '국어교육과', '수학교육과', '영어교육과', '역사교육과',
  '동양화과', '회화과', '판화과', '조소과', '디자인학부', '금속조형디자인과', '도예유리과',
  '목조형가구학과', '섬유미술패션디자인과', '예술학과', '뮤지컬전공', '실용음악전공', '디자인예술경영학부'
);

-- 확인용
-- SELECT COUNT(*) FROM user_board_subscriptions;
-- SELECT source_id, COUNT(*) FROM user_board_subscriptions GROUP BY source_id ORDER BY 2 DESC;
