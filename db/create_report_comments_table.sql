-- ============================================
-- report_comments / report_comment_flags: 제보 댓글과 댓글 신고 (PR feat/report-comments)
-- ReportComment.java, ReportCommentFlag.java 엔티티와 필드 단위로 맞춤.
-- ddl-auto=validate 환경이므로 이 PR 을 배포하기 전에 RDS 에서 먼저 실행해야 함.
-- 옛 서버는 이 테이블을 모르므로 먼저 실행해도 안전. 다시 실행해도 안전(IF NOT EXISTS).
-- reports, users 테이블이 먼저 있어야 함.
--
-- 삭제 규칙(개인정보 최소 보관):
--   - 제보가 지워지면(작성자 삭제·작성자 탈퇴) 그 제보의 댓글·댓글 신고도 함께 삭제 (report_id ON DELETE CASCADE)
--   - 댓글 작성자가 탈퇴하면 그 사람의 댓글·신고도 함께 삭제 (user_id ON DELETE CASCADE)
--   - 댓글이 지워지면 그 댓글의 신고도 함께 삭제 (comment_id ON DELETE CASCADE)
--   - 답글은 한 단계만(parent_id 는 항상 최상위 댓글). 최상위 댓글 행이 지워지면 답글도 함께 삭제 (parent_id ON DELETE CASCADE)
--     (작성자·관리자 삭제는 status=DELETED 로 남기므로, 행이 실제로 지워지는 건 제보 삭제·탈퇴 때뿐)
--
-- 확인:   SHOW CREATE TABLE report_comments; SHOW CREATE TABLE report_comment_flags;
-- 되돌리기(서버를 되돌린 뒤): report_comment_flags, report_comments 순서로 테이블 제거
-- ============================================

CREATE TABLE IF NOT EXISTS `report_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `parent_id` bigint NULL,
  `content` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'VISIBLE',
  `reviewed_at` datetime NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_report_comments_report` (`report_id`, `parent_id`, `status`, `id`),
  KEY `ix_report_comments_parent` (`parent_id`, `status`, `id`),
  KEY `ix_report_comments_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_report_comments_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_comments_parent` FOREIGN KEY (`parent_id`) REFERENCES `report_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_comments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `report_comment_flags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `reason` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_report_comment_flags` (`comment_id`, `user_id`),
  KEY `ix_report_comment_flags_user` (`user_id`),
  CONSTRAINT `fk_report_comment_flags_comment` FOREIGN KEY (`comment_id`) REFERENCES `report_comments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_comment_flags_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
