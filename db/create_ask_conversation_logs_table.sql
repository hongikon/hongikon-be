-- ============================================
-- ask_conversation_logs: 물어보기 서버 답 기록 — 2026-10-16
-- POST /ask/route·/ask/route/stream 한 번에 한 행. 가린 질문·답·근거·살펴본 과정·토큰을 모아 안내·프롬프트 개선과 회귀 평가에 쓴다.
-- 코드: ask.AskConversationLog (필드 단위 대조 완료). 조회 예시: docs/ask-conversation-queries.sql
--
-- - 개인정보: 질문·답·과정은 저장 전에 가린다(학번·휴대전화·이메일·주민번호·8자리 이상 숫자). user_id·IP 는 두지 않는다.
-- - conversation_id 는 앱 탭마다 만든 임의 값(같은 대화끼리만 잇는다). resolved 는 '해결됐나요' 응답(NULL=아직).
-- - 보관 365일, 매일 03:50(UTC) 지난 행을 지운다(AskConversationRecorder.purgeExpired).
-- - ddl-auto=validate 라 이 기능이 든 서버를 배포하기 전에 실행해야 한다. 여러 번 실행해도 안전하다.
-- ============================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ask_conversation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(64) COLLATE utf8mb4_unicode_ci NULL,
  `turn` int NOT NULL,
  `question` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer` text COLLATE utf8mb4_unicode_ci NULL,
  `outcome` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `node_ids` varchar(1000) COLLATE utf8mb4_unicode_ci NULL,
  `reference_titles` text COLLATE utf8mb4_unicode_ci NULL,
  `steps` text COLLATE utf8mb4_unicode_ci NULL,
  `latency_ms` bigint NOT NULL,
  `input_tokens` bigint NOT NULL,
  `output_tokens` bigint NOT NULL,
  `cache_read_tokens` bigint NOT NULL,
  `catalog_version` varchar(20) COLLATE utf8mb4_unicode_ci NULL,
  `resolved` boolean NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ask_conversation_logs_created_at` (`created_at`),
  KEY `idx_ask_conversation_logs_conversation` (`conversation_id`),
  KEY `idx_ask_conversation_logs_outcome` (`outcome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
