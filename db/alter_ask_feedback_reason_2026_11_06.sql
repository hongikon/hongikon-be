-- ============================================
-- 물어보기 피드백 개선 — 2026-11-06
-- - ask_conversation_logs.reason / reviewed_at : '해결 안 됨' 이유(WRONG·OUTDATED·OFF_TOPIC·NOT_ENOUGH), 관리자 확인 시각
-- - ask_feedback_logs.client_id / reason / reviewed_at : 앱 안내 흐름 답의 같은 응답 잇기(임의 값), 이유, 확인 시각
-- - feedback.ask_ref : '운영진에게 물어보기' 문의가 어느 답에서 왔는지('route:{id}' / 'flow:{client_id}')
-- 코드: AskConversationLog·AskFeedbackLog·Feedback. 개인정보: 사람을 가리키는 값 없음(client_id 는 응답마다 새로 만든 임의 값).
-- ddl-auto=validate 라 이 서버를 배포하기 전에 RDS 에서 먼저 실행한다. 옛 서버는 이 컬럼들을 모르므로 먼저 실행해도 안전, 다시 실행해도 안전.
-- ============================================
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS hongikon_add_col;
DELIMITER //
CREATE PROCEDURE hongikon_add_col(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl TEXT)
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) = 0 THEN
    SET @s := ddl; PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

CALL hongikon_add_col('ask_conversation_logs', 'reason',
  'ALTER TABLE `ask_conversation_logs` ADD COLUMN `reason` varchar(20) COLLATE utf8mb4_unicode_ci NULL');
CALL hongikon_add_col('ask_conversation_logs', 'reviewed_at',
  'ALTER TABLE `ask_conversation_logs` ADD COLUMN `reviewed_at` datetime(6) NULL');
CALL hongikon_add_col('ask_feedback_logs', 'client_id',
  'ALTER TABLE `ask_feedback_logs` ADD COLUMN `client_id` varchar(64) COLLATE utf8mb4_unicode_ci NULL, ADD KEY `idx_ask_feedback_logs_client` (`client_id`)');
CALL hongikon_add_col('ask_feedback_logs', 'reason',
  'ALTER TABLE `ask_feedback_logs` ADD COLUMN `reason` varchar(20) COLLATE utf8mb4_unicode_ci NULL');
CALL hongikon_add_col('ask_feedback_logs', 'reviewed_at',
  'ALTER TABLE `ask_feedback_logs` ADD COLUMN `reviewed_at` datetime(6) NULL');
CALL hongikon_add_col('feedback', 'ask_ref',
  'ALTER TABLE `feedback` ADD COLUMN `ask_ref` varchar(80) COLLATE utf8mb4_unicode_ci NULL, ADD KEY `idx_feedback_ask_ref` (`ask_ref`)');

DROP PROCEDURE IF EXISTS hongikon_add_col;
