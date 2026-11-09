-- ============================================
-- app_notices: 앱 공지사항(설정 > 공지사항, 앱을 켤 때 팝업) — 2026-11-09 (feat/app-notices)
-- 관리자가 /admin/notices 에서 쓰고 publish_at(게시 시각)부터 앱에 보인다. 앱은 GET /notices 로 받는다.
-- 코드: notice.AppNotice (필드 단위 대조 완료).
--
-- ■ 시각(publish_at, popup_until)은 다른 테이블처럼 UTC.
-- ■ popup_until 이 있으면 publish_at ~ popup_until 동안 앱을 켤 때 popup_text 를 팝업으로 한 번 띄운다.
-- ■ ddl-auto=validate 라 배포 전에 실행해야 한다. 여러 번 실행해도 안전하다(없을 때만 만든다).
-- ============================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `app_notices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` varchar(5000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `publish_at` datetime(6) NOT NULL,
  `popup_until` datetime(6) NULL,
  `popup_text` varchar(300) COLLATE utf8mb4_unicode_ci NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_app_notices_publish_at` (`publish_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인: SHOW CREATE TABLE app_notices;
