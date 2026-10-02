-- ============================================
-- 제보 커뮤니티 기능 (PR feat/report-community, base feat/report-comments #19)
--   report_reactions       : 🔥(불) — 제보마다 한 사람 한 번, 다시 누르면 행 삭제
--   report_follows         : 관심 제보 — 시작·곧 끝남 알림, 새 댓글 알림(묶음)
--   report_engagement      : 제보별 상태 1행 — 조회 수(익명 집계), 작성자 알림 켜짐, 🔥 이정표(10·50·100) 알림 진행
--   report_view_marks      : 조회 수 하루 1회 중복 방지 표식. 사용자 id·설치 id 원문이 아니라 날짜를 섞은 HMAC 만 저장,
--                            2일 지난 행은 스케줄러가 지운다. IP 는 저장하지 않는다.
--   report_comment_likes   : 댓글·답글 👍 — 댓글마다 한 사람 한 번
-- 엔티티(ReportReaction, ReportFollow, ReportEngagement, ReportViewMark, ReportCommentLike)와 필드 단위로 맞춤.
-- ddl-auto=validate 이므로 이 PR 을 배포하기 전에 RDS 에서 먼저 실행. 옛 서버는 이 테이블을 모르므로 먼저 실행해도 안전.
-- 다시 실행해도 안전(IF NOT EXISTS). reports, users, report_comments(#19) 가 먼저 있어야 함.
--
-- 삭제 규칙(개인정보 최소 보관): 모든 FK ON DELETE CASCADE
--   - 제보가 지워지면(작성자 삭제·탈퇴) 그 제보의 🔥·관심·조회 기록·상태 행이 함께 삭제
--   - 회원이 탈퇴하면 그 사람의 🔥·관심·👍 이 함께 삭제
--   - 댓글 행이 지워지면 그 댓글의 👍 이 함께 삭제
--
-- 확인:   SHOW CREATE TABLE report_reactions; (나머지 4개도 같은 방법)
-- 되돌리기(서버를 되돌린 뒤):
--   DROP TABLE report_comment_likes, report_view_marks, report_engagement, report_follows, report_reactions;
-- ============================================

CREATE TABLE IF NOT EXISTS `report_reactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_reactions` (`report_id`, `user_id`),
  KEY `ix_report_reactions_report_created` (`report_id`, `created_at`),
  KEY `ix_report_reactions_user` (`user_id`),
  CONSTRAINT `fk_report_reactions_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_reactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_follows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `start_notified` boolean NOT NULL DEFAULT FALSE,
  `ending_notified` boolean NOT NULL DEFAULT FALSE,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_follows` (`report_id`, `user_id`),
  KEY `ix_report_follows_user` (`user_id`),
  CONSTRAINT `fk_report_follows_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_follows_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_engagement` (
  `report_id` bigint NOT NULL,
  `view_count` bigint NOT NULL DEFAULT 0,
  `author_notify_enabled` boolean NOT NULL DEFAULT TRUE,
  `fire_milestone_sent` int NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`report_id`),
  CONSTRAINT `fk_report_engagement_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_view_marks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `viewer_key` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `view_date` date NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_view_marks` (`report_id`, `viewer_key`, `view_date`),
  KEY `ix_report_view_marks_date` (`view_date`),
  CONSTRAINT `fk_report_view_marks_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_comment_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_comment_likes` (`comment_id`, `user_id`),
  KEY `ix_report_comment_likes_user` (`user_id`),
  CONSTRAINT `fk_report_comment_likes_comment` FOREIGN KEY (`comment_id`) REFERENCES `report_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_comment_likes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
