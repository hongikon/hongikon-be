-- ============================================
-- ask_feedback_logs: 챗봇 "물어보기" 질문·피드백 로그 — 2026-10-15 (feat/ask-feedback)
-- POST /ask/feedback 이 한 행씩 쌓는다. 못 찾은 질문(outcome = NONE)과 "해결 안 됨"(resolved = 0)을 모아
-- 안내 흐름 추가와 LLM 도입 여부를 판단하는 익명 통계용.
-- 코드: ask.AskFeedbackLog (필드 단위 대조 완료).
--
-- - user_id 를 두지 않는다 — 질문 원문에 개인정보가 섞일 수 있어 누가 보냈는지는 남기지 않는다.
-- - resolved 는 NULL(답변 직후, 아직 응답 전) / 1(해결) / 0(해결 안 됨).
-- - 조회 예시는 docs/ask-feedback-queries.sql.
-- - ddl-auto=validate 라 배포 전에 실행해야 한다. 여러 번 실행해도 안전하다(없을 때만 만든다).
-- ============================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ask_feedback_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `outcome` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `flow_id` varchar(100) COLLATE utf8mb4_unicode_ci NULL,
  `node_id` varchar(100) COLLATE utf8mb4_unicode_ci NULL,
  `resolved` boolean NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ask_feedback_logs_created_at` (`created_at`),
  KEY `idx_ask_feedback_logs_outcome` (`outcome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
