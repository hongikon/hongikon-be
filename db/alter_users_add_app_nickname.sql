-- ============================================
-- users.app_nickname: 사용자가 앱에서 직접 정하는 공개용 닉네임(선택) (2026-10-02)
--   - NULL 이면 제보 작성자 등 공개 화면에 카카오/Apple 닉네임을 첫 글자만 남기고 가려서 보여 준다(User#getDisplayName).
--   - 유니크 인덱스: 다른 사람 닉네임 사칭 방지. 컬럼 collation 이 utf8mb4_unicode_ci 라 대소문자를 가리지 않고 중복으로 본다.
--     NULL 은 여러 행이어도 된다.
-- ddl-auto=validate 환경이므로 배포 순서가 중요하다:
--   1) 이 ALTER를 운영 DB에 먼저 실행
--   2) 그다음 feat/app-nickname 이 들어간 백엔드 배포 (먼저 배포하면 컬럼이 없어 기동 시 스키마 검증 실패)
-- 컬럼 추가만 하므로 기존 버전 백엔드가 떠 있는 상태에서 실행해도 안전하다.
-- 확인: SHOW COLUMNS FROM users LIKE 'app_nickname';
-- ============================================

ALTER TABLE `users`
    ADD COLUMN `app_nickname` VARCHAR(30) COLLATE utf8mb4_unicode_ci NULL,
    ADD UNIQUE KEY `uq_users_app_nickname` (`app_nickname`);
