-- ============================================
-- ask_operator_notes: 운영진 확인 안내 — 2026-11-06
-- 운영진이 관리자 '물어보기 피드백'에서 직접 쓴 질문·답. 물어보기가 비슷한 질문에 답할 때 근거로 함께 읽는다(page:note-{id}).
-- 코드: ask.AskOperatorNote. 지우지 않고 끈다(active=0). created_by 는 등록한 관리자 회원 번호(학생 아님).
-- 개인정보: 저장 전에 휴대전화·주민등록번호·학번 형태를 거절한다(AskPrivacy.personalKind).
-- ddl-auto=validate 라 이 서버를 배포하기 전에 RDS 에서 먼저 실행한다. 여러 번 실행해도 안전하다.
-- ============================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ask_operator_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ask_ref` varchar(80) COLLATE utf8mb4_unicode_ci NULL,
  `active` boolean NOT NULL DEFAULT TRUE,
  `created_by` bigint NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ask_operator_notes_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
