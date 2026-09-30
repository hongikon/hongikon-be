-- ============================================
-- 관리자 화면(hongikon.com/admin)용 스키마 변경. (2026-09-30)
--   1) users.role          — 관리자 구분(USER/ADMIN). User.java
--   2) reports.moderation_note, reports.reviewed_at — 제보 검토 기록. Report.java
--   3) feedback 테이블     — 앱 문의하기(POST /feedback) 저장. Feedback.java
--
-- ddl-auto=validate 환경이므로 배포 전 이 스크립트를 실제 DB에 직접 실행해야 함.
-- 실행하지 않고 배포하면 컬럼/테이블 불일치로 서버가 기동하지 않는다.
-- reports 테이블이 먼저 있어야 함 (db/create_reports_table.sql).
-- ============================================

ALTER TABLE `users`
  ADD COLUMN `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER';

ALTER TABLE `reports`
  ADD COLUMN `moderation_note` varchar(200) COLLATE utf8mb4_unicode_ci NULL,
  ADD COLUMN `reviewed_at` datetime NULL;

CREATE TABLE `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NULL,
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact` varchar(100) COLLATE utf8mb4_unicode_ci NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` datetime NULL,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_status_created` (`status`, `created_at`),
  KEY `user_id` (`user_id`),
  -- 탈퇴해도 문의 내용은 남기고 작성자만 비운다.
  CONSTRAINT `feedback_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 관리자 지정 (카카오로 한 번 로그인해 users 에 행이 생긴 뒤, 본인 id 를 확인해서 실행)
--   SELECT id, nickname, created_at FROM users ORDER BY created_at DESC LIMIT 10;
--   UPDATE users SET role = 'ADMIN' WHERE id = ?;
